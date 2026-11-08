package com.nexus.core.writeoff;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.web.MoneyJson;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for goods written off as a loss (damaged, lost). */
@RestController
@RequestMapping("/api/v1/write-offs")
class WriteOffController {

    /** Give either a unit (U-…) or a lot (LOT-…). */
    record NewWriteOff(String unitId, String lotId, String reason, LocalDate date) {
    }

    record WriteOffView(String id, String unitId, String lotId, long productId, int quantity, String reason,
                        LocalDate date, List<MoneyJson> cost) {
        static WriteOffView of(WriteOff w) {
            return new WriteOffView(w.publicId(), w.unitId(), w.lotId(), w.productId(), w.quantity(), w.reason(),
                    w.date(), w.cost().stream().map(MoneyJson::of).toList());
        }
    }

    private final WriteOffService writeOffs;

    WriteOffController(WriteOffService writeOffs) {
        this.writeOffs = writeOffs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WriteOffView create(@RequestBody NewWriteOff request) {
        boolean unit = request.unitId() != null && !request.unitId().isBlank();
        boolean lot = request.lotId() != null && !request.lotId().isBlank();
        if (unit == lot) {
            throw new BusinessRuleException("Give either unitId or lotId");
        }
        WriteOff created = unit
                ? writeOffs.writeOffUnit(request.unitId().strip(), request.reason(), request.date())
                : writeOffs.writeOffLot(request.lotId().strip(), request.reason(), request.date());
        return WriteOffView.of(created);
    }

    @GetMapping
    List<WriteOffView> list() {
        return writeOffs.listWriteOffs().stream().map(WriteOffView::of).toList();
    }
}
