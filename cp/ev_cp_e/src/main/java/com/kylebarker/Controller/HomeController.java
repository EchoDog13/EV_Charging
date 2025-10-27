package com.kylebarker.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        // redirect to the static index.html so the page is served without a template
        // engine
        return "redirect:/index.html";
    }
}
