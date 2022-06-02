package com.icthh.xm.tmf.ms.activation.config;

import lombok.experimental.UtilityClass;

/**
 * Application constants.
 */
@UtilityClass
public final class Constants {

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_.@A-Za-z0-9-]*$";

    public static final String SYSTEM_ACCOUNT = "system";
    public static final String ANONYMOUS_USER = "anonymoususer";
    public static final String DEFAULT_LANGUAGE = "en";

    public static final String GENERAL_ERROR_CODE = "general.error";
}
