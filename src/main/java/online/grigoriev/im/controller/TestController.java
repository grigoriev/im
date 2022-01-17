package online.grigoriev.im.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "api", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestController {

    @GetMapping(value = "/test")
    public String test() {
        return "All good. You can see this because you are Authenticated.";
    }
}
