package org.hongtu.config;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 为当前进程申请唯一的雪花节点号。申请不到时拒绝启动，避免多实例生成相同主键。
 */
public final class SnowflakeWorkerLease {

    private SnowflakeWorkerLease() {
    }

    public static Lease acquire(DataSource dataSource) {
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
            long leaseStartNanos = System.nanoTime();
            Node owned = claim(connection, instanceId);
            if (owned == null) {
                connection.rollback();
                throw new IllegalStateException("没有可用的雪花节点号，拒绝启动");
            }
            connection.commit();
            Lease lease = new Lease(owned, leaseStartNanos);
            startHeartbeat(dataSource, lease, instanceId);
            return lease;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("申请雪花节点号失败，拒绝启动", exception);
        }
    }

    private static Node claim(Connection connection, String instanceId) throws Exception {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT worker_id, datacenter_id FROM snowflake_worker_lease
                WHERE expires_at < NOW() OR instance_id = ?
                ORDER BY expires_at
                LIMIT 1 FOR UPDATE
                """)) {
            select.setString(1, instanceId);
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    int workerId = rows.getInt(1);
                    int datacenterId = rows.getInt(2);
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE snowflake_worker_lease
                            SET instance_id = ?, expires_at = DATE_ADD(NOW(), INTERVAL 60 SECOND)
                            WHERE worker_id = ? AND datacenter_id = ?
                            """)) {
                        update.setString(1, instanceId);
                        update.setInt(2, workerId);
                        update.setInt(3, datacenterId);
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
                        VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 60 SECOND))
                        """)) {
                    insert.setInt(1, workerId);
                    insert.setInt(2, datacenterId);
                    insert.setString(3, instanceId);
                    insert.executeUpdate();
                    return new Node(workerId, datacenterId);
                } catch (Exception ignored) {
                    // 节点已被占用，继续找下一个。
                }
            }
        }
        return null;
    }

    private static void startHeartbeat(DataSource dataSource, Lease lease, String instanceId) {
        Thread heartbeat = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !lease.lost.get()) {
                try {
                    Thread.sleep(20000);
                    long renewalStartNanos = System.nanoTime();
                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement update = connection.prepareStatement("""
                                 UPDATE snowflake_worker_lease SET expires_at = DATE_ADD(NOW(), INTERVAL 60 SECOND)
                                 WHERE worker_id = ? AND datacenter_id = ? AND instance_id = ?
                                 """)) {
                        update.setInt(1, lease.node.workerId());
                        update.setInt(2, lease.node.datacenterId());
                        update.setString(3, instanceId);
                        if (update.executeUpdate() != 1) {
                            lease.lost.set(true);
                            throw new IllegalStateException("雪花节点租约已丢失");
                        }
                        lease.renew(renewalStartNanos);
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

    /** 续约失败超过安全期限后立即停止发号；租约被夺取后永久失效。 */
    public static final class Lease {
        private final Node node;
        private final AtomicLong safeUntilNanos = new AtomicLong();
        private final AtomicBoolean lost = new AtomicBoolean();

        private Lease(Node node, long leaseStartNanos) {
            this.node = node;
            renew(leaseStartNanos);
        }

        private void renew(long leaseStartNanos) {
            // 数据库租约为 60 秒，提前 5 秒停止发号，留出时钟与调度余量。
            safeUntilNanos.set(leaseStartNanos + TimeUnit.SECONDS.toNanos(55));
        }

        public Node node() {
            return node;
        }

        public void requireValid() {
            if (lost.get() || System.nanoTime() - safeUntilNanos.get() >= 0) {
                throw new IllegalStateException("雪花节点租约无效，已停止生成ID");
            }
        }
    }
}
