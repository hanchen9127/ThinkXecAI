package com.thinkxecai.exception;

import lombok.Getter;

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
