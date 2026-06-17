package com.study.grabthisforme.service.view;

import java.math.BigDecimal;
import java.util.List;

public record UserStoreSummaryView(
    IdentityView identity,
    LocationView location,
    CommercialInfoView commercialInfo,
    StatisticsView statistics
) {

    public record IdentityView(
        Long id,
        String name,
        String type,
        Long ownerId
    ) {
    }

    public record LocationView(
        String address,
        Double latitude,
        Double longitude
    ) {
    }

    public record CommercialInfoView(
        String phone,
        String businessHours,
        BigDecimal minOrderAmount,
        BigDecimal deliveryFee,
        Boolean isOpen,
        String pic,
        Float rating,
        List<String> tags
    ) {
    }

    public record StatisticsView(
        Long salesVolume
    ) {
    }
}
