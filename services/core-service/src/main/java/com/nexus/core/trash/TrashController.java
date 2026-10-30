package com.nexus.core.trash;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows what was deleted, when and why, so it can be restored. */
@RestController
@RequestMapping("/api/v1")
class TrashController {

    private final TrashRepository trash;

    TrashController(TrashRepository trash) {
        this.trash = trash;
    }

    @GetMapping("/trash")
    List<TrashItem> list() {
        return trash.findAll();
    }
}
