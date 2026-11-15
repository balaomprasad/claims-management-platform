package com.claimsphere.document;

import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentIntegrationTest extends IntegrationTestSupport {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private TestUser claimant;
    private TestUser otherClaimant;
    private TestUser officer;
    private String claimId;

    @BeforeEach
    void setUp() throws Exception {
        claimant = createUser(Role.CLAIMANT);
        otherClaimant = createUser(Role.CLAIMANT);
        officer = createUser(Role.CLAIMS_OFFICER);
        claimId = submitClaim(claimant, createActivePolicy(claimant.id(), new BigDecimal("5000")));
    }

    @Test
    void claimantUploadsAndStaffDownloadsDocument() throws Exception {
        String body = upload(claimant, new MockMultipartFile("file", "../../repair quote.pdf",
                "application/octet-stream", PDF), "REPAIR_ESTIMATE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("repair quote.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.checksumSha256").isString())
                .andReturn().getResponse().getContentAsString();
        String documentId = readJson(body).get("id").asText();

        mockMvc.perform(get("/api/v1/claims/{id}/documents", claimId).header("Authorization", officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("REPAIR_ESTIMATE"));

        byte[] content = mockMvc.perform(get("/api/v1/claims/{id}/documents/{doc}/content", claimId, documentId)
                        .header("Authorization", officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(content).isEqualTo(PDF);
    }

    @Test
    void otherClaimantCannotSeeOrUpload() throws Exception {
        mockMvc.perform(get("/api/v1/claims/{id}/documents", claimId).header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isNotFound());
        upload(otherClaimant, new MockMultipartFile("file", "x.pdf", "application/pdf", PDF), "OTHER")
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDisguisedExecutableAndEmptyFiles() throws Exception {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};
        upload(claimant, new MockMultipartFile("file", "invoice.pdf", "application/pdf", exe), "INVOICE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_DOCUMENT_TYPE"));
        upload(claimant, new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]), "INVOICE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("EMPTY_DOCUMENT"));
        upload(claimant, new MockMultipartFile("file", "x.pdf", "application/pdf", PDF), "SELFIE")
                .andExpect(status().isBadRequest());
    }

    @Test
    void closedClaimAcceptsNoDocuments() throws Exception {
        postAs(claimant, "/api/v1/claims/{id}/withdraw", null, claimId).andExpect(status().isOk());

        upload(claimant, new MockMultipartFile("file", "x.pdf", "application/pdf", PDF), "OTHER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_CLOSED"));
    }

    private org.springframework.test.web.servlet.ResultActions upload(TestUser user, MockMultipartFile file,
                                                                      String type) throws Exception {
        return mockMvc.perform(multipart("/api/v1/claims/{id}/documents", claimId).file(file)
                .param("documentType", type).header("Authorization", user.bearer())
                .contentType(MediaType.MULTIPART_FORM_DATA));
    }
}
