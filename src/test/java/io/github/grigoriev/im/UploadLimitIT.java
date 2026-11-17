package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** The upload limit over real HTTP: a large upload ends before the server reads its body. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"im.admin.username=chef", "im.admin.password=chef-passwort-123"})
@Import(TestDatabase.class)
class UploadLimitIT {

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/admin/backup/import", "/expenses"})
    void anAnonymousUploadOfFourGigabytesEndsAtOnce(String path) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: multipart/form-data; boundary=x\r\nContent-Length: 4294967296\r\n\r\n--x\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String status = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                    .readLine();
            assertThat(status).isEqualTo("HTTP/1.1 413 ");
        }
    }
}
