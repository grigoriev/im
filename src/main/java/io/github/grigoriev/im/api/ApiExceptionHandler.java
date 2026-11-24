package io.github.grigoriev.im.api;

import io.github.grigoriev.im.customer.CustomerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers a refused request of the API as RFC 9457 problem details, with every problem of the input. The exceptions of
 * Spring MVC, such as a body that does not parse, get their problem details from the base class; any other exception
 * gets a 500 without its message, which goes to the log.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
     * Answers an unexpected failure. The message stays in the log: it may name internals.
     *
     * @param e the failure
     * @return 500
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail failure(Exception e) {
        LOG.error("API request failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ProblemResponse.detail(500));
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
