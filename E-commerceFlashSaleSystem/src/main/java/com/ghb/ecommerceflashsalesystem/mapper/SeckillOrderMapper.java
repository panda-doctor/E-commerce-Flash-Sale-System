package com.ghb.ecommerceflashsalesystem.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

/**
 * <p>
 * 秒杀订单表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-07-29
 */
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    SeckillOrder selectByOrderNo(@Param("orderNo") String orderNo);
}


/**
 * @Override 是 Java语言规范 的注解（编译时）。
 *
 * @Autowired 是 Spring框架 的注解（运行时）。
 *
 * 为了让你彻底搞懂，我分别从作用和核心区别两个维度来拆解：
 *
 * 1. 各自的作用（职责）
 * @Override（重写标记）
 *
 * 目标：方法。
 *
 * 作用：告诉编译器，这个方法要重写（Override）父类中的方法，或者实现接口中的抽象方法。
 *
 * 核心价值：编译期防错。如果你写的方法名拼写错了，或者参数列表与父类不匹配，编译器会立刻报错，防止你以为自己重写了，实际上却定义了一个新方法。
 *
 * 例子：toString() 写成 tostring()，加上 @Override 编译器会直接标红。
 *
 * @Autowired（依赖注入）
 *
 * 目标：构造方法、Setter方法、字段（属性）。
 *
 * 作用：告诉Spring容器，我需要一个Bean，请把匹配的Bean自动“注入”给我。
 *
 * 核心价值：解耦。你不用自己 new 对象，也不用写繁琐的 getBean()，Spring会在运行时帮你把对象组装好。
 *
 * 例子：在 UserService 中加 @Autowired 标记 UserDao，Spring运行时就会把数据库操作对象自动塞进去。
 */
