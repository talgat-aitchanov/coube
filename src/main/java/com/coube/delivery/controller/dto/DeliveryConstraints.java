package com.coube.delivery.controller.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DeliveryConstraints {

    public static final String DISTANCE_KM_MIN = "1";
    public static final String DISTANCE_KM_MAX = "5000";
    public static final String WEIGHT_TON_MIN = "0.1";
    public static final String WEIGHT_TON_MAX = "120";
}
