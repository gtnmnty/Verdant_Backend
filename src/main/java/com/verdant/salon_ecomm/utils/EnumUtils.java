package com.verdant.salon_ecomm.utils;

public class EnumUtils {
  public static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
    try {
      return Enum.valueOf(enumClass, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
    }
  }
}
