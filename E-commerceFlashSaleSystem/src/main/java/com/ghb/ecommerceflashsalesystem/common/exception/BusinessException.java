package com.ghb.ecommerceflashsalesystem.common.exception;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import lombok.Getter;
/**
 * BusinessException（异常类）
 */

/**
 * 业务异常，用于业务层抛出可预期的错误（如库存不足、重复秒杀），
 * 由全局异常处理器捕获并转换为统一的 API 响应。
 */
@Getter
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码枚举
     */
    private final ResultCode resultCode;

    /**
     * 异常消息（可能覆盖枚举默认描述）
     */
    private final String message;

    /**
     * 使用枚举默认描述构造业务异常。
     *
     * @param resultCode 业务状态码枚举
     */
    public BusinessException(ResultCode resultCode) {
        this(resultCode, resultCode.getDescription());
    }

    /**
     * 使用自定义消息构造业务异常，覆盖枚举默认描述。
     *
     * @param resultCode 业务状态码枚举
     * @param message    自定义异常消息（将作为响应中的 message）
     */
    public BusinessException(ResultCode resultCode, String message) {
        this.resultCode = resultCode;
        this.message = message;
    }
    /**
     * 获取数字状态码，方便日志和响应直接使用。
     *
     * @return 状态码数字
     */
    public int getCode(){
        return resultCode.getCode();
    }
    @Override
    public String getMessage() {
        return message;
    }
}
