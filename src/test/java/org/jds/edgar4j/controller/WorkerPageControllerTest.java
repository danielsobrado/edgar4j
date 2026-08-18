package org.jds.edgar4j.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkerPageControllerTest {

    @Test
    void workerShortcutRedirectsToStaticEntryPage() {
        WorkerPageController controller = new WorkerPageController();

        assertEquals("redirect:/worker/index.html", controller.workerPage());
    }
}
