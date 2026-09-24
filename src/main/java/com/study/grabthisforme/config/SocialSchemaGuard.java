package com.study.grabthisforme.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.*;

/** Prevent Hibernate update from silently switching an unmigrated legacy database. */
@Configuration
public class SocialSchemaGuard {
  @Bean
  static EntityManagerFactoryDependsOnPostProcessor socialSchemaDependency() {
    return new EntityManagerFactoryDependsOnPostProcessor("socialSchemaCheck");
  }

  @Bean
  Object socialSchemaCheck(DataSource source) throws Exception {
    try (var c = source.getConnection();
        var s = c.createStatement();
        var r =
            s.executeQuery(
                "select count(*) from information_schema.columns where table_name='CONVERSATION'"
                    + " and column_name='TARGET_ID'")) {
      r.next();
      if (r.getInt(1) > 0) {
        try (var q = c.createStatement();
            var v = q.executeQuery("select count(*) from social_schema_version where version=1")) {
          v.next();
          if (v.getInt(1) != 1) throw new IllegalStateException("Migration missing");
        } catch (Exception e) {
          throw new IllegalStateException(
              "Legacy social schema detected. Back up and run SocialSchemaMigration audit/apply"
                  + " before startup; see docs/social-schema-migration.md",
              e);
        }
      }
    }
    return new Object();
  }
}
