package com.autotrading

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 순수 DB 커넥션 검증 (TASK 02-A). "키 채우면 붙는다"만 확인한다.
 *
 * JPA/Hibernate를 띄우지 않고 [DataSourceAutoConfiguration]만 올린다.
 * 전체 컨텍스트(@SpringBootTest 전체)를 띄우면 연결 실패 시 Hibernate가
 * "Unable to determine Dialect" 로 진짜 JDBC 에러(인증 실패·host 오타 등)를
 * 덮어버려 디버깅이 어렵다. DataSource만 주입하면 실패 시 PSQLException이 그대로 보인다.
 *
 * 환경변수(SUPABASE_DB_PASSWORD)가 없으면 스킵 — CI에서 비밀값 없이 빌드가 깨지지 않게.
 */
@SpringBootTest(classes = [DataSourceAutoConfiguration::class])
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class DbConnectionTest {

    @Autowired
    lateinit var dataSource: DataSource

    @Test
    fun `datasource connects and answers SELECT 1`() {
        dataSource.connection.use { conn ->
            assertTrue(conn.isValid(5), "connection should be valid")
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT 1").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }
}