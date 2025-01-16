package com.atguigu.daijia.rules;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author ：zxl
 * @Description:
 * @ClassName: ServiceRulesTests
 * @date ：2025/01/14 23:33
 */
@SpringBootTest
class ServiceRulesApplicationTests {
    @Resource
    private KieContainer kieContainer;
    @Test
    void test1() {
        // 开启会话
        KieSession kieSession = kieContainer.newKieSession();
        // 触发规则
        kieSession.fireAllRules();
        // 中止会话
        kieSession.dispose();
    }

}
