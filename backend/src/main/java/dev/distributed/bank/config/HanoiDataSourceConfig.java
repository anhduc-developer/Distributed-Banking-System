package dev.distributed.bank.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Cấu hình DataSource cho SITE 1: Chi nhánh Hà Nội.
 *
 * Tạo 3 bean:
 * 1. DataSource — kết nối MySQL Hà Nội (port 3307)
 * 2. JdbcTemplate — để chạy SQL trên MySQL Hà Nội
 * 3. TransactionManager — quản lý transaction trên MySQL Hà Nội
 *
 * @Primary: đánh dấu đây là DataSource mặc định (Spring cần 1 cái primary)
 */
@Configuration
public class HanoiDataSourceConfig {

    @Bean(name = "hanoiDataSource")
    @Primary
    public DataSource hanoiDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(
                "jdbc:mysql://localhost:3307/bank_hanoi?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
        ds.setUsername("root");
        ds.setPassword("root");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setConnectionTimeout(5000);
        ds.setMaximumPoolSize(10);
        ds.setPoolName("HanoiPool");
        return ds;
    }

    @Bean(name = "hanoiJdbcTemplate")
    @Primary
    public JdbcTemplate hanoiJdbcTemplate(@Qualifier("hanoiDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "hanoiTransactionManager")
    @Primary
    public PlatformTransactionManager hanoiTransactionManager(
            @Qualifier("hanoiDataSource") DataSource ds) {

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);

        txManager.setTransactionSynchronization(
                DataSourceTransactionManager.SYNCHRONIZATION_NEVER);

        return txManager;
    }
}
