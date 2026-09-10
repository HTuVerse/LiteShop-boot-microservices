package org.hongtu.announcement.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.announcement.dto.AnnouncementRecord;
import org.hongtu.announcement.dto.AnnouncementAttachment;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AnnouncementMapper {

    @Insert("""
            INSERT INTO sys_announcement
              (id, tenant_id, title, content, level, status, publisher_id, publisher_name, version,
               publish_time, expire_time, created_at, updated_at)
            VALUES
              (#{id}, #{tenantId}, #{title}, #{content}, #{level}, #{status}, #{publisherId}, #{publisherName}, #{version},
               #{publishTime}, #{expireTime}, #{createdAt}, #{updatedAt})
            """)
    int insertAnnouncement(AnnouncementRecord record);

    @Update("""
            UPDATE sys_announcement
            SET title=#{title}, content=#{content}, level=#{level}, expire_time=#{expireTime},
                version=version+1, updated_at=#{updatedAt}
            WHERE id=#{id} AND status='DRAFT'
            """)
    int updateDraft(AnnouncementRecord record);

    @Update("""
            UPDATE sys_announcement
            SET status='PUBLISHED', publisher_id=#{publisherId}, publisher_name=#{publisherName},
                publish_time=#{publishTime}, version=version+1, updated_at=#{publishTime}
            WHERE id=#{id} AND status IN ('DRAFT','WITHDRAWN')
            """)
    int publish(@Param("id") Long id,
                @Param("publisherId") Long publisherId,
                @Param("publisherName") String publisherName,
                @Param("publishTime") LocalDateTime publishTime);

    @Update("""
            UPDATE sys_announcement
            SET status='WITHDRAWN', version=version+1, updated_at=#{updatedAt}
            WHERE id=#{id} AND status='PUBLISHED'
            """)
    int withdraw(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    @Delete("DELETE FROM sys_announcement WHERE id=#{id} AND status='DRAFT'")
    int deleteDraft(@Param("id") Long id);

    @Select("""
            SELECT id, tenant_id AS tenantId, title, content, level, status,
                   publisher_id AS publisherId, publisher_name AS publisherName,
                   version, publish_time AS publishTime,
                   expire_time AS expireTime, created_at AS createdAt, updated_at AS updatedAt
            FROM sys_announcement WHERE id=#{id}
            """)
    AnnouncementRecord findById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, tenant_id AS tenantId, title, content, level, status,
                   publisher_id AS publisherId, publisher_name AS publisherName,
                   version, publish_time AS publishTime,
                   expire_time AS expireTime, created_at AS createdAt, updated_at AS updatedAt
            FROM sys_announcement
            WHERE
              <choose>
                <when test="tenantId == null">tenant_id IS NULL</when>
                <otherwise>tenant_id=#{tenantId}</otherwise>
              </choose>
              <if test="status != null and status != ''">AND status=#{status}</if>
              <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY created_at DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<AnnouncementRecord> findAdminPage(@Param("tenantId") Long tenantId,
                                            @Param("status") String status,
                                            @Param("keyword") String keyword,
                                            @Param("offset") long offset,
                                            @Param("size") long size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_announcement
            WHERE
              <choose>
                <when test="tenantId == null">tenant_id IS NULL</when>
                <otherwise>tenant_id=#{tenantId}</otherwise>
              </choose>
              <if test="status != null and status != ''">AND status=#{status}</if>
              <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </script>
            """)
    long countAdmin(@Param("tenantId") Long tenantId,
                    @Param("status") String status,
                    @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT a.id, a.tenant_id AS tenantId, a.title, a.content, a.level, a.status,
                   a.publisher_id AS publisherId, a.publisher_name AS publisherName,
                   a.version, a.publish_time AS publishTime,
                   a.expire_time AS expireTime, a.created_at AS createdAt, a.updated_at AS updatedAt,
                   CASE WHEN r.user_id IS NULL THEN FALSE ELSE TRUE END AS `read`
            FROM sys_announcement a
            LEFT JOIN sys_announcement_read r
              ON r.announcement_id=a.id AND r.user_id=#{userId}
            WHERE a.status='PUBLISHED'
              AND a.publish_time &lt;= NOW()
              AND (a.expire_time IS NULL OR a.expire_time &gt; NOW())
              AND (a.tenant_id IS NULL
                <if test="tenantId != null">OR a.tenant_id=#{tenantId}</if>)
              AND (
                NOT EXISTS (SELECT 1 FROM sys_announcement_target t WHERE t.announcement_id=a.id)
                <if test="unrestricted">OR TRUE</if>
                <if test="!unrestricted and storeIds != null and storeIds.size() > 0">
                  OR EXISTS (
                    SELECT 1 FROM sys_announcement_target t
                    WHERE t.announcement_id=a.id AND t.target_type='STORE'
                      AND t.target_id IN
                      <foreach collection="storeIds" item="storeId" open="(" separator="," close=")">
                        #{storeId}
                      </foreach>
                  )
                </if>
              )
            ORDER BY a.publish_time DESC, a.id DESC
            LIMIT #{size}
            </script>
            """)
    List<AnnouncementRecord> findCurrent(@Param("tenantId") Long tenantId,
                                          @Param("userId") Long userId,
                                          @Param("unrestricted") boolean unrestricted,
                                          @Param("storeIds") List<Long> storeIds,
                                          @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_announcement a
            LEFT JOIN sys_announcement_read r
              ON r.announcement_id=a.id AND r.user_id=#{userId}
            WHERE r.user_id IS NULL
              AND a.status='PUBLISHED'
              AND a.publish_time &lt;= NOW()
              AND (a.expire_time IS NULL OR a.expire_time &gt; NOW())
              AND (a.tenant_id IS NULL
                <if test="tenantId != null">OR a.tenant_id=#{tenantId}</if>)
              AND (
                NOT EXISTS (SELECT 1 FROM sys_announcement_target t WHERE t.announcement_id=a.id)
                <if test="unrestricted">OR TRUE</if>
                <if test="!unrestricted and storeIds != null and storeIds.size() > 0">
                  OR EXISTS (
                    SELECT 1 FROM sys_announcement_target t
                    WHERE t.announcement_id=a.id AND t.target_type='STORE'
                      AND t.target_id IN
                      <foreach collection="storeIds" item="storeId" open="(" separator="," close=")">
                        #{storeId}
                      </foreach>
                  )
                </if>
              )
            </script>
            """)
    long countUnread(@Param("tenantId") Long tenantId,
                     @Param("userId") Long userId,
                     @Param("unrestricted") boolean unrestricted,
                     @Param("storeIds") List<Long> storeIds);

    @Select("SELECT target_id FROM sys_announcement_target WHERE announcement_id=#{announcementId} AND target_type='STORE' ORDER BY target_id")
    List<Long> findStoreTargets(@Param("announcementId") Long announcementId);

    @Delete("DELETE FROM sys_announcement_target WHERE announcement_id=#{announcementId}")
    int deleteTargets(@Param("announcementId") Long announcementId);

    @Insert("INSERT INTO sys_announcement_target (id, announcement_id, target_type, target_id) VALUES (#{id}, #{announcementId}, 'STORE', #{storeId})")
    int insertStoreTarget(@Param("id") Long id,
                          @Param("announcementId") Long announcementId,
                          @Param("storeId") Long storeId);

    @Select("""
            SELECT id, file_name AS fileName, file_url AS fileUrl, file_size AS fileSize,
                   content_type AS contentType, sort_order AS sortOrder
            FROM sys_announcement_attachment
            WHERE announcement_id=#{announcementId}
            ORDER BY sort_order, id
            """)
    List<AnnouncementAttachment> findAttachments(@Param("announcementId") Long announcementId);

    @Delete("DELETE FROM sys_announcement_attachment WHERE announcement_id=#{announcementId}")
    int deleteAttachments(@Param("announcementId") Long announcementId);

    @Insert("""
            INSERT INTO sys_announcement_attachment
              (id, announcement_id, file_name, file_url, file_size, content_type, sort_order)
            VALUES
              (#{id}, #{announcementId}, #{fileName}, #{fileUrl}, #{fileSize}, #{contentType}, #{sortOrder})
            """)
    int insertAttachment(@Param("id") Long id,
                         @Param("announcementId") Long announcementId,
                         @Param("fileName") String fileName,
                         @Param("fileUrl") String fileUrl,
                         @Param("fileSize") Long fileSize,
                         @Param("contentType") String contentType,
                         @Param("sortOrder") Integer sortOrder);

    @Insert("""
            INSERT INTO sys_announcement_read (announcement_id, user_id, tenant_id, read_at)
            VALUES (#{announcementId}, #{userId}, #{tenantId}, NOW())
            ON DUPLICATE KEY UPDATE read_at=VALUES(read_at), tenant_id=VALUES(tenant_id)
            """)
    int markRead(@Param("announcementId") Long announcementId,
                 @Param("userId") Long userId,
                 @Param("tenantId") Long tenantId);
}
