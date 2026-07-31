package com.baronesa.emporio.converter;

import com.baronesa.emporio.enums.ProductSignageStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductSignageStatusConverter implements AttributeConverter<ProductSignageStatus, String> {

    @Override
    public String convertToDatabaseColumn(ProductSignageStatus attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public ProductSignageStatus convertToEntityAttribute(String dbData) {
        return ProductSignageStatus.fromValue(dbData);
    }
}
