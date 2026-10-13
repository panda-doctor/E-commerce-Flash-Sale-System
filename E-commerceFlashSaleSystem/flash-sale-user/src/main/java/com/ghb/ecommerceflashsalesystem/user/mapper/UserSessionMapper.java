package com.ghb.ecommerceflashsalesystem.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
    @Select("SELECT * FROM user_session WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > #{now}")
    UserSession findActive(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Update("UPDATE user_session SET revoked_at = #{now} WHERE token_hash = #{tokenHash} AND revoked_at IS NULL")
    int revoke(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Update("UPDATE user_session SET revoked_at = #{now} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE user_session SET csrf_token_hash = #{csrfTokenHash} WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > #{now}")
    int rotateCsrf(@Param("tokenHash") String tokenHash, @Param("csrfTokenHash") String csrfTokenHash,
                   @Param("now") LocalDateTime now);

    @Delete("DELETE FROM user_session WHERE expires_at < #{cutoff} OR revoked_at < #{cutoff}")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
