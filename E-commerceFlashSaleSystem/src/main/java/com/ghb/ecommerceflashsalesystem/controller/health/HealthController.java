package com.ghb.ecommerceflashsalesystem.controller.health;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器，提供应用、MySQL、Redis 的存活探测接口。
 * <p>
 * 接口路径：GET /api/health
 * 返回格式：{@link Result} 包装的 Map，包含三个键：
 * <ul>
 *   <li>application: 永远为 UP（能调用即存活）</li>
 *   <li>mysql: UP/DOWN</li>
 *   <li>redis: UP/DOWN</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 健康检查接口
     *
     * @return 统一响应体，data 为包含三个状态键值对的 Map
     */

    @GetMapping("/api/health")
    public Result<Map<String, String>> health() {
        Map<String, String> statusMap = new LinkedHashMap<>();
        //应用状态： 能响应请求即为 UP
        statusMap.put("application", "UP");

        // 探测 MySQL
        statusMap.put("mysql", checkMysql());

        //redis
        statusMap.put("redis", checkRedis());

        return Result.success(statusMap);

    }

    /**
     * 检查 MySQL 连接是否可用
     *
     * @return "UP" 或 "DOWN"
     */
    private String checkMysql() {
        // 使用 try-with-resources 自动归还连接
        try(Connection connection = dataSource.getConnection()){
            // isValid 超时设为 1 秒，避免长时间阻塞
            boolean valid = connection.isValid(1);
            return valid?"UP":"DOWN";
        } catch (SQLException e){
            log.warn("MySQL 连接检查失败: {}", e.getMessage());
            return "DOWN";
        }
    }

    /**
     * 检查 Redis 连接是否可用
     *
     * @return "UP" 或 "DOWN"
     */
    private String checkRedis() {
        try (var connection = redisTemplate.getConnectionFactory().getConnection()){
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong)?"UP":"DOWN";
        }catch (Exception e){
            log.warn("Redis 连接检查失败: {}", e.getMessage());
            return "DOWN";
        }
    }


}
