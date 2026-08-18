package org.jds.edgar4j.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkerPageController {

    @GetMapping("/worker")
    public String workerPage() {
        return "redirect:/worker/index.html";
    }
}
