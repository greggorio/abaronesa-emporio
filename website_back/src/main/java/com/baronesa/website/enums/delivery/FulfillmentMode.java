package com.baronesa.website.enums.delivery;

public enum FulfillmentMode {
    DELIVERY,
    PICKUP;

    public static FulfillmentMode from(FulfillmentMode mode) {
        return mode == null ? DELIVERY : mode;
    }
}
