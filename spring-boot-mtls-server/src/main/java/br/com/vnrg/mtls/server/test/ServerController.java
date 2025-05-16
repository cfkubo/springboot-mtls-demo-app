package br.com.vnrg.mtls.server.test;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServerController {

    @GetMapping(path = "/todo")
    public String get() {
        return "Hello World";
    }
}
