package com.verdant.salon_ecomm.utils;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;

public class EnumUtils {
  public static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
    if (value == null) {
      throw BusinessRuleException.invalid("Missing " + fieldName);
    }

    try {
      return Enum.valueOf(enumClass, value);
    } catch (IllegalArgumentException e) {
      throw BusinessRuleException.invalid("Invalid " + fieldName + ": " + value);
    }
  }
}
