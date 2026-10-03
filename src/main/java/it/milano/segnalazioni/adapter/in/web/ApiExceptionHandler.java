package it.milano.segnalazioni.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException e) {
        Map<String, Object> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "richiesta non valida", "campi", errors));
    }

    /**
     * Dispatch failures are the interesting ones: a report the service accepted but could
     * not deliver. The citizen is told plainly rather than shown a success they did not get.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onDispatchFailure(IllegalStateException e) {
        log.error("Dispatch failure", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "la segnalazione e' stata registrata ma non e' stato possibile inoltrarla",
                "detail", String.valueOf(e.getMessage())));
    }
}
