package org.hongtu.config;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 为当前进程申请唯一的雪花节点号。申请不到时拒绝启动，避免多实例生成相同主键。
 */
public final class SnowflakeWorkerLease {

    private SnowflakeWorkerLease() {
    }

    public static Node acquire(DataSource dataSource) {
        String instanceId = instanceId();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS snowflake_worker_lease (
                          worker_id int NOT NULL COMMENT '雪花 worker，0-31',
                          datacenter_id int NOT NULL COMMENT '雪花 datacenter，0-31',
                          instance_id varchar(128) NOT NULL COMMENT '持有实例',
                          expires_at datetime NOT NULL COMMENT '租约过期时间',
                          PRIMARY KEY (worker_id, datacenter_id)
                        ) COMMENT='雪花节点租约'
                        """);
            }
            Node owned = claim(connection, instanceId);
            if (owned == null) {
                connection.rollback();
                throw new IllegalStateException("没有可用的雪花节点号，拒绝启动");
            }
            connection.commit();
            startHeartbeat(dataSource, owned, instanceId);
            return owned;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("申请雪花节点号失败，拒绝启动", exception);
        }
    }

    private static Node claim(Connection connection, String instanceId) throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp until = Timestamp.from(Instant.now().plus(60, ChronoUnit.SECONDS));
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT worker_id, datacenter_id FROM snowflake_worker_lease
                WHERE expires_at < ? OR instance_id = ?
                ORDER BY expires_at
                LIMIT 1 FOR UPDATE
                """)) {
            select.setTimestamp(1, now);
            select.setString(2, instanceId);
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    int workerId = rows.getInt(1);
                    int datacenterId = rows.getInt(2);
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE snowflake_worker_lease
                            SET instance_id = ?, expires_at = ?
                            WHERE worker_id = ? AND datacenter_id = ?
                            """)) {
                        update.setString(1, instanceId);
                        update.setTimestamp(2, until);
                        update.setInt(3, workerId);
                        update.setInt(4, datacenterId);
                        update.executeUpdate();
                    }
                    return new Node(workerId, datacenterId);
                }
            }
        }
        for (int datacenterId = 0; datacenterId <= 31; datacenterId++) {
            for (int workerId = 0; workerId <= 31; workerId++) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO snowflake_worker_lease (worker_id, datacenter_id, instance_id, expires_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    insert.setInt(1, workerId);
                    insert.setInt(2, datacenterId);
                    insert.setString(3, instanceId);
                    insert.setTimestamp(4, until);
                    insert.executeUpdate();
                    return new Node(workerId, datacenterId);
                } catch (Exception ignored) {
                    // 节点已被占用，继续找下一个。
                }
            }
        }
        return null;
    }

    private static void startHeartbeat(DataSource dataSource, Node node, String instanceId) {
        Thread heartbeat = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(20000);
                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement update = connection.prepareStatement("""
                                 UPDATE snowflake_worker_lease SET expires_at = ?
                                 WHERE worker_id = ? AND datacenter_id = ? AND instance_id = ?
                                 """)) {
                        update.setTimestamp(1, Timestamp.from(Instant.now().plus(60, ChronoUnit.SECONDS)));
                        update.setInt(2, node.workerId());
                        update.setInt(3, node.datacenterId());
                        update.setString(4, instanceId);
                        if (update.executeUpdate() != 1) {
                            throw new IllegalStateException("雪花节点租约已丢失");
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception exception) {
                    System.err.println("雪花节点续约失败: " + exception.getMessage());
                }
            }
        }, "snowflake-worker-lease");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    private static String instanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ProcessHandle.current().pid() + ":" + UUID.randomUUID();
        } catch (Exception exception) {
            return UUID.randomUUID().toString();
        }
    }

    public record Node(int workerId, int datacenterId) {
    }
}
