package com.meetingmate.app.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomic "use once": only one caller can flip revoked_at from NULL, so two requests
     * presenting the same token at the same moment cannot both rotate it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") Instant now,
                       @Param("reason") RefreshToken.RevokeReason reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now,
                     @Param("reason") RefreshToken.RevokeReason reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now,
                         @Param("reason") RefreshToken.RevokeReason reason);

    @Query("select t.id from RefreshToken t where t.expiresAt < :cutoff order by t.id")
    List<Long> findExpiredIds(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable page);

    @Transactional
    @Modifying
    @Query("delete from RefreshToken t where t.id in :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
