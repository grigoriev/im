package io.github.grigoriev.im.api;

import io.github.grigoriev.im.customer.CustomerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Answers a refused request of the API as RFC 9457 problem details, with every problem of the input. */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler {

    /**
     * Answers a refused customer.
     *
     * @param e the problems
     * @return 400 with the list {@code problems}
     */
    @ExceptionHandler(CustomerException.class)
    public ProblemDetail customer(CustomerException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("problems", e.problems());
        return problem;
    }

    /**
     * Answers a request for something that does not exist.
     *
     * @param e what is missing
     * @return 404
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
