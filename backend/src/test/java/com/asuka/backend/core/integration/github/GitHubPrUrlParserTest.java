package com.asuka.backend.core.integration.github;

import com.asuka.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubPrUrlParserTest {

    @Test
    void parsesValidPrUrl() {
        GitHubPrUrlParser.PrUrl url = GitHubPrUrlParser.parse(
                "https://github.com/octocat/Hello-World/pull/11596");
        assertEquals("octocat", url.owner());
        assertEquals("Hello-World", url.repo());
        assertEquals(11596, url.number());
        assertEquals("octocat/Hello-World", url.repoFullName());
    }

    @Test
    void allowsTrailingSlashQueryAndFragment() {
        GitHubPrUrlParser.PrUrl url = GitHubPrUrlParser.parse(
                "https://github.com/octocat/Hello-World/pull/11596/?utm=test#files");
        assertEquals(11596, url.number());
    }

    @Test
    void rejectsInvalidUrls() {
        assertThrows(BusinessException.class, () -> GitHubPrUrlParser.parse(
                "http://github.com/octocat/Hello-World/pull/1"));
        assertThrows(BusinessException.class, () -> GitHubPrUrlParser.parse(
                "https://gitlab.com/octocat/Hello-World/pull/1"));
        assertThrows(BusinessException.class, () -> GitHubPrUrlParser.parse(
                "https://github.com/octocat/Hello-World/issues/1"));
        assertThrows(BusinessException.class, () -> GitHubPrUrlParser.parse(
                "https://github.com/octocat/Hello-World/pull/abc"));
    }
}
