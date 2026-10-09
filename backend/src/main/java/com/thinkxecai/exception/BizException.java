package com.thinkxecai.exception;

import lombok.Getter;

/**
 * A failure with a known cause. With a client error code (400 / 404 / 409) the message is returned
 * to the caller, so it must not contain internal details. Without a code it is an internal failure:
 * the message is only logged and the caller gets a generic 500.
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = null;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

}
