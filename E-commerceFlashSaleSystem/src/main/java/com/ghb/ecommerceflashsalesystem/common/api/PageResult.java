package com.ghb.ecommerceflashsalesystem.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 * 统一分页响应体，作为 {@link Result#success()}中的 data 字段，
 * 封装分页数据及分页元信息，确保前端统一解析分页结构。
 *
 * @param <T> 记录类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResult<T> {
    /**
     * 当前页数据列表
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码（从 1 开始）
     */
    private long pageNo;

    /**
     * 每页条数
     */
    private long pageSize;

    /**
     * 总页数（由 total 和 pageSize 计算得出）
     */
    private long pages;

    // ---------- 私有构造，禁止外部直接 new ----------
    private PageResult(){
        // 无参构造，供 Jackson 反序列化使用
    }

    // ---------- 静态工厂方法 ----------

    /**
     * 从 MyBatis-Plus 的 {@link Page} 对象构造分页结果。
     *
     * @param page MyBatis-Plus 分页查询结果（不能为 {@code null}）
     * @param <T>  记录类型
     * @return 分页响应体
     */
    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNo(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());   // MyBatis-Plus 已计算好
        return result;
    }

    /**
     * 手动构造分页结果（兜底方案，适用于非 MyBatis-Plus 场景）。
     * 总页数由 {@code (total + pageSize - 1) / pageSize} 计算，并做除数保护。
     *
     * @param records  当前页数据列表
     * @param total    总记录数
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页条数（必须大于 0，否则总页数置为 0）
     * @param <T>      记录类型
     * @return 分页响应体
     */
    public static <T> PageResult<T> of(List<T> records, long total, long pageNo, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        // 计算总页数，防止 pageSize 为 0
        long pages = (pageSize > 0) ? (total + pageSize - 1) / pageSize : 0;
        result.setPages(pages);
        return result;
    }
}
