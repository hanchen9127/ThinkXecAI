package com.thinkxecai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Recipient checks and outcomes, with the Gmail sender mocked: nothing here touches the network. */
class EmailToolTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NOT_ONE_ADDRESS = "Not sent: \"to\" must be exactly one email address.";

    private final GmailSender gmailSender = mock(GmailSender.class);
    private final EmailTool tool = new EmailTool(gmailSender, " Alice@Example.com , , bob@example.com ");

    @BeforeEach
    void configured() {
        when(gmailSender.missingSettings()).thenReturn(List.of());
        when(gmailSender.send(any(), anyString(), anyString())).thenReturn("msg-1");
    }

    @Test
    void anAllowedRecipientGetsTheEmailAndTheModelGetsGmailsId() {
        assertThat(tool.sendEmail("alice@example.com", " Report ", "Body"))
                .isEqualTo("Sent to alice@example.com (Gmail message id msg-1).");
        verify(gmailSender).send(address("alice@example.com"), "Report", "Body");
    }

    @Test
    void theAllowlistIgnoresCase() {
        assertThat(tool.sendEmail("BOB@EXAMPLE.COM", "S", "B")).startsWith("Sent to BOB@EXAMPLE.COM");
    }

    @Test
    void aDisplayNameIsDroppedAndOnlyTheAddressIsUsed() {
        assertThat(tool.sendEmail("Alice Smith <alice@example.com>", "S", "B")).startsWith("Sent to alice@example.com");

        ArgumentCaptor<InternetAddress> recipient = ArgumentCaptor.forClass(InternetAddress.class);
        verify(gmailSender).send(recipient.capture(), anyString(), anyString());
        assertThat(recipient.getValue().getPersonal()).isNull();
    }

    @Test
    void anotherAddressIsRefusedWithoutRevealingTheAllowlist() {
        String answer = tool.sendEmail("mallory@evil.example", "S", "B");

        assertThat(answer).isEqualTo("Not sent: mallory@evil.example is not an allowed recipient.");
        assertThat(answer).doesNotContainIgnoringCase("alice").doesNotContainIgnoringCase("bob");
        verify(gmailSender, never()).send(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "alice@example.com, mallory@evil.example",
            "alice@example.com;mallory@evil.example",
            "friends: alice@example.com, mallory@evil.example;",
            "alice@example.com\r\nBcc: mallory@evil.example",
            "not an address",
            "alice@"})
    void anythingButExactlyOneValidAddressIsRefused(String to) {
        assertThat(tool.sendEmail(to, "S", "B")).isEqualTo(NOT_ONE_ADDRESS);
        verify(gmailSender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void anEmptyAllowlistRefusesEverySend() {
        EmailTool closed = new EmailTool(gmailSender, "");

        assertThat(closed.sendEmail("alice@example.com", "S", "B"))
                .isEqualTo("Not sent: alice@example.com is not an allowed recipient.");
        verify(gmailSender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void missingGmailSettingsAreNamedBeforeTheAllowlistIsChecked() {
        when(gmailSender.missingSettings()).thenReturn(List.of("THINKXECAI_GMAIL_CLIENT_SECRET", "THINKXECAI_GMAIL_REFRESH_TOKEN"));

        assertThat(tool.sendEmail("mallory@evil.example", "S", "B")).isEqualTo(
                "Not sent: email is not configured on this server. Missing environment variables: "
                        + "THINKXECAI_GMAIL_CLIENT_SECRET, THINKXECAI_GMAIL_REFRESH_TOKEN.");
        verify(gmailSender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void blankArgumentsAndMultiLineSubjectsAreRefused() {
        assertThat(tool.sendEmail(" ", "S", "B")).isEqualTo("Not sent: to, subject, and content must all be given.");
        assertThat(tool.sendEmail("alice@example.com", null, "B")).isEqualTo("Not sent: to, subject, and content must all be given.");
        assertThat(tool.sendEmail("alice@example.com", "S", "")).isEqualTo("Not sent: to, subject, and content must all be given.");
        assertThat(tool.sendEmail("alice@example.com", "Hi\r\nBcc: mallory@evil.example", "B"))
                .isEqualTo("Not sent: the subject must be a single line.");
        verify(gmailSender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void aSendFailureIsReturnedToTheModel() {
        when(gmailSender.send(any(), anyString(), anyString()))
                .thenThrow(new GmailSender.SendFailure("Not sent: Gmail rejected the message (HTTP 400)."));

        assertThat(tool.sendEmail("alice@example.com", "S", "B")).isEqualTo("Not sent: Gmail rejected the message (HTTP 400).");
    }

    @Test
    void anUnexpectedErrorIsNotThrownIntoTheAgentLoop() {
        when(gmailSender.send(any(), anyString(), anyString())).thenThrow(new IllegalStateException("boom"));

        assertThat(tool.sendEmail("alice@example.com", "S", "B")).isEqualTo("Not sent: an internal error occurred.");
    }

    @Test
    void theModelSeesSendEmailWithToSubjectAndContent() throws Exception {
        ToolCallback callback = MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks()[0];
        JsonNode schema = JSON.readTree(callback.getToolDefinition().inputSchema());

        assertThat(tool.getName()).isEqualTo("emailTool");
        assertThat(tool.getType()).isEqualTo(ToolType.OPTIONAL);
        assertThat(callback.getToolDefinition().name()).isEqualTo("sendEmail");
        assertThat(schema.get("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("to", "subject", "content");
    }

    private static InternetAddress address(String text) {
        try {
            return new InternetAddress(text, true);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
