package com.study.grabthisforme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.grabthisforme.service.*;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.common.ApiException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CommentOutboxTests {
    @Autowired AuthService auth;
    @Autowired PostService posts;
    @Autowired CommentSendService sends;
    @Autowired CommentSendReceiptRepository receipts;
    @Autowired PostCommentRepository comments;
    @Autowired PostStatsRepository stats;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthService.AuthResult user() { return auth.register("outbox_"+UUID.randomUUID(),"Testpass123","Tester",null,null); }
    String topic(long user) { return posts.createPost(user,"评论测试",List.of(),null,"SHARE",List.of(),null,null,"","","","","").postId(); }

    @Test void lostResponseRetryReturnsSameCommentAndOneCount() {
        var a=user(); var p=topic(a.user().id()); var key=UUID.randomUUID().toString();
        var first=sends.comment(a.user().id(),p,key,"同一评论",List.of(),"广东");
        var retry=sends.comment(a.user().id(),p,key,"同一评论",List.of(),"广东");
        assertThat(retry).isEqualTo(first);
        assertThat(retry.clientRequestId()).isEqualTo(key);
        assertThat(comments.countByPostId(p)).isEqualTo(1);
        assertThat(stats.findById(p).orElseThrow().commentCount).isEqualTo(1);
    }
    @Test void concurrentDuplicateHasExactlyOneEffect() throws Exception {
        var a=user(); var p=topic(a.user().id()); var key=UUID.randomUUID().toString();
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Future<Long>> jobs=new ArrayList<>();
            for(int i=0;i<4;i++) jobs.add(executor.submit(()->{ gate.await(); return sends.comment(a.user().id(),p,key,"并发",List.of(),"").commentId(); }));
            gate.countDown();
            Set<Long> ids=new HashSet<>();
            for(var job:jobs) ids.add(job.get(15,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        }
        assertThat(comments.countByPostId(p)).isEqualTo(1);
        assertThat(stats.findById(p).orElseThrow().commentCount).isEqualTo(1);
    }
    @Test void sameKeyDifferentPayloadIsRejectedButDifferentAccountIsIndependent() {
        var a=user(); var b=user(); var p=topic(a.user().id()); var key=UUID.randomUUID().toString();
        sends.comment(a.user().id(),p,key,"第一条",List.of(),"");
        assertThatThrownBy(()->sends.comment(a.user().id(),p,key,"修改内容",List.of(),"")).isInstanceOf(ApiException.class);
        sends.comment(b.user().id(),p,key,"另一账号",List.of(),"");
        assertThat(comments.countByPostId(p)).isEqualTo(2);
        assertThat(stats.findById(p).orElseThrow().commentCount).isEqualTo(2);
    }
    @Test void repliesDeduplicateAndRejectForeignParentWithoutReceipt() {
        var a=user(); var p=topic(a.user().id()); var other=topic(a.user().id());
        var parent=sends.comment(a.user().id(),p,UUID.randomUUID().toString(),"父评论",List.of(),"");
        var key=UUID.randomUUID().toString();
        var r=sends.reply(a.user().id(),p,key,parent.commentId(),null,"回复",List.of(),a.user().id());
        assertThat(sends.reply(a.user().id(),p,key,parent.commentId(),null,"回复",List.of(),a.user().id())).isEqualTo(r);
        assertThat(stats.findById(p).orElseThrow().commentCount).isEqualTo(2);
        var bad=UUID.randomUUID().toString();
        assertThatThrownBy(()->sends.reply(a.user().id(),other,bad,parent.commentId(),null,"错误目标",List.of(),a.user().id()))
            .isInstanceOf(ApiException.class);
        assertThat(receipts.findByUserIdAndRequestId(a.user().id(),bad)).isEmpty();
        assertThatThrownBy(()->sends.comment(a.user().id(),p,key,"回复编号不能发评论",List.of(),"")).isInstanceOf(ApiException.class);
    }
    @Test void failureRollsBackReceiptAndComment() {
        var a=user(); var p=topic(a.user().id()); var key=UUID.randomUUID().toString();
        var saved=stats.findById(p).orElseThrow(); stats.deleteById(p);
        assertThatThrownBy(()->sends.comment(a.user().id(),p,key,"回滚",List.of(),"")).isInstanceOf(ApiException.class);
        assertThat(comments.countByPostId(p)).isZero();
        assertThat(receipts.findByUserIdAndRequestId(a.user().id(),key)).isEmpty();
        stats.saveAndFlush(saved);
        sends.comment(a.user().id(),p,key,"回滚",List.of(),"");
        assertThat(comments.countByPostId(p)).isEqualTo(1);
    }
    @Test void httpUsesTokenIdentityAndReturnsKeyInList() throws Exception {
        var a=user(); var p=topic(a.user().id()); var key=UUID.randomUUID().toString();
        mvc.perform(get("/api/posts/send-capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts/send-capabilities").header("Authorization","Bearer "+a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(1));
        var body=json.writeValueAsString(Map.of("message","HTTP评论","clientRequestId",key,"userId",99999));
        mvc.perform(post("/api/posts/"+p+"/comments").header("Authorization","Bearer "+a.token()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.commenter.id").value(a.user().id()))
            .andExpect(jsonPath("$.data.clientRequestId").value(key));
        mvc.perform(get("/api/posts/"+p+"/comments").header("Authorization","Bearer "+a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].clientRequestId").value(key));
        mvc.perform(post("/api/posts/"+p+"/comments").header("Authorization","Bearer "+a.token()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("message","不同内容","clientRequestId",key))))
            .andExpect(status().isConflict());
    }
    @Test void legacyClientsRemainCompatible() {
        var a=user(); var p=topic(a.user().id());
        assertThat(sends.comment(a.user().id(),p,null,"旧版",List.of(),"").commentId()).isPositive();
    }

    @Test void acceptedTextLengthFitsStorageAndOversizeIsRejected() {
        var a=user(); var p=topic(a.user().id()); var key=UUID.randomUUID().toString();
        var text="文".repeat(1000);
        var sent=sends.comment(a.user().id(),p,key,text,List.of(),"");
        assertThat(comments.findById(sent.commentId()).orElseThrow().message).isEqualTo(text);
        assertThatThrownBy(()->sends.comment(a.user().id(),p,UUID.randomUUID().toString(),"文".repeat(10001),List.of(),""))
            .isInstanceOf(ApiException.class);
        assertThat(comments.countByPostId(p)).isEqualTo(1);
    }
}
