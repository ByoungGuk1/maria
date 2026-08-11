package com.app.maria.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D2(현금 직접입금 차단): account.amount를 변경하는 <update>가 아래 3개 외에 새로 생기면 실패한다.
 * 이 3개는 전부 실제 매도/환전/인출 레코드에서 계산된 값만 반영하며, 임의 금액을 받는 입금 경로는 없다.
 * 새 write path를 추가하려면 이 목록에 의도적으로 추가해야 한다(실수로 생긴 직접입금 경로 차단).
 */
class AccountAmountWritePathTest {

    private static final Set<String> ALLOWED_ACCOUNT_AMOUNT_WRITERS = Set.of(
            "updateProvisionalAmount",
            "replaceAccountAmount",
            "deductAccountAmount"
    );

    private static final Pattern TARGETS_ACCOUNT_TABLE = Pattern.compile("(?i)\\bupdate\\s+account\\b");
    // \b는 '_'를 단어문자로 취급하므로 limit_amount/final_amount 등은 매칭되지 않는다
    private static final Pattern TOUCHES_AMOUNT_COLUMN = Pattern.compile("(?i)\\bamount\\b");

    @Test
    @DisplayName("account.amount를 변경하는 <update>는 정해진 3개(가환전/확정산/인출 차감)뿐이다")
    void onlyKnownWritersModifyAccountAmount() throws Exception {
        Set<String> actualWriters = new HashSet<>();

        for (Resource resource : findMapperXmlResources()) {
            Document document = parse(resource);
            NodeList updates = document.getElementsByTagName("update");
            for (int i = 0; i < updates.getLength(); i++) {
                Element update = (Element) updates.item(i);
                String sql = update.getTextContent();
                if (TARGETS_ACCOUNT_TABLE.matcher(sql).find() && TOUCHES_AMOUNT_COLUMN.matcher(sql).find()) {
                    actualWriters.add(update.getAttribute("id"));
                }
            }
        }

        assertThat(actualWriters).containsExactlyInAnyOrderElementsOf(ALLOWED_ACCOUNT_AMOUNT_WRITERS);
    }

    private Resource[] findMapperXmlResources() throws IOException {
        return new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml");
    }

    private Document parse(Resource resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // MyBatis DTD를 네트워크로 받으러 가지 않도록 빈 entity로 대체
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        try (InputStream inputStream = resource.getInputStream()) {
            return builder.parse(inputStream);
        }
    }
}
