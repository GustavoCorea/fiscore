package com.fiscore.core.controller;

import com.fiscore.core.models.Compra;
import com.fiscore.core.services.CompraService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<?> listar(@RequestParam int anio, @RequestParam(required = false) Integer mes) {
        return ResponseEntity.ok(compraService.findByPeriodo(anio, mes));
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return compraService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> guardar(@RequestBody Compra compra) {
        compra.setId(null);
        Compra guardada = compraService.guardar(compra);
        return ResponseEntity.ok(Map.of("message", "Compra registrada", "id", guardada.getId()));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Compra compra) {
        if (compraService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        compra.setId(id);
        compraService.guardar(compra);
        return ResponseEntity.ok(Map.of("message", "Compra actualizada", "id", id));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        compraService.eliminar(id);
        return ResponseEntity.ok(Map.of("message", "Compra eliminada"));
    }

    @GetMapping("/proveedores")
    @ResponseBody
    public ResponseEntity<?> proveedores() {
        return ResponseEntity.ok(compraService.proveedoresConocidos());
    }

    @GetMapping("/libro")
    @ResponseBody
    public ResponseEntity<?> libro(@RequestParam int anio) {
        return ResponseEntity.ok(compraService.getLibroCompras(anio));
    }

    @GetMapping("/liquidacion")
    @ResponseBody
    public ResponseEntity<?> liquidacion(@RequestParam int anio) {
        return ResponseEntity.ok(compraService.getLiquidacionIva(anio));
    }

    /** Libro de compras en CSV, para adjuntarlo a la declaración. */
    @GetMapping("/libro.csv")
    @ResponseBody
    public ResponseEntity<String> libroCsv(@RequestParam int anio) {
        StringBuilder csv = new StringBuilder(
                "Mes;Gravado interno;Gravado importacion;Exento;Credito fiscal;Total;Documentos\n");

        for (Map<String, Object> fila : compraService.getLibroCompras(anio)) {
            csv.append(fila.get("mes")).append(';')
               .append(fila.get("gravadoInterno")).append(';')
               .append(fila.get("gravadoImportacion")).append(';')
               .append(fila.get("exento")).append(';')
               .append(fila.get("creditoFiscal")).append(';')
               .append(fila.get("total")).append(';')
               .append(fila.get("documentos")).append('\n');
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"libro-compras-" + anio + ".csv\"")
                .body(csv.toString());
    }

    /** Años ofrecidos en el selector, del actual hacia atrás. */
    public static List<Integer> aniosDisponibles() {
        int actual = LocalDate.now().getYear();
        return List.of(actual, actual - 1, actual - 2);
    }

    /** Solo para la vista: importe con dos decimales o cero. */
    public static BigDecimal dosDecimales(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
