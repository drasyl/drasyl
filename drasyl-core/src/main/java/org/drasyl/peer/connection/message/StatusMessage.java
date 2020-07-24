/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Status that representing a HTTP/1.1 response status code, defined in RFC 7231 section 6 (<a href=
 * "https://tools.ietf.org/html/rfc7231#section-6">https://tools.ietf.org/html/rfc7231#section-6</a>).
 *
 * <p>
 * <i> Quote: </i>
 * </p>
 *
 * <p>
 * <i> The status-code element is a positive three-digit integer code giving the
 * result of the attempt to understand and satisfy the request. </i>
 * </p>
 *
 * <p>
 * <i> HTTP status codes are extensible. HTTP clients are not required to
 * understand the meaning of all registered status codes, though such understanding is obviously
 * desirable. However, a client MUST understand the class of any status code, as indicated by the
 * first digit, and treat an unrecognized status code as being equivalent to the x00 status code of
 * that class, with the exception that a recipient MUST NOT cache a response with an unrecognized
 * status code. </i>
 * </p>
 *
 * <p>
 * <i> For example, if an unrecognized status code of 471 is received by a
 * client, the client can assume that there was something wrong with its request and treat the
 * response as if it had received a 400 (Bad Request) status code. The response message will usually
 * contain a representation that explains the status. </i>
 * </p>
 *
 * <p>
 * <i> The first digit of the status-code defines the class of response. The
 * last two digits do not have any categorization role. There are five values for the first digit:
 * </i>
 * <ul>
 * <li><i>1xx (Informational): The request was received, continuing
 * process</i></li>
 * <li><i>2xx (Successful): The request was successfully received, understood,
 * and accepted</i></li>
 * <li><i>3xx (Redirection): Further action needs to be taken in order to
 * complete the request</i></li>
 * <li><i>4xx (Client Error): The request contains bad syntax or cannot be
 * fulfilled</i></li>
 * <li><i>5xx (Server Error): The server failed to fulfill an apparently valid
 * request</i></li>
 * </ul>
 * </p>
 */
public class StatusMessage extends AbstractResponseMessage<RequestMessage> {
    private final Code code;

    @JsonCreator
    private StatusMessage(@JsonProperty("id") MessageId id,
                          @JsonProperty("code") Code code,
                          @JsonProperty("correspondingId") MessageId correspondingId) {
        super(id, correspondingId);
        this.code = requireNonNull(code);
    }

    public StatusMessage(int code, MessageId correspondingId) {
        this(Code.from(code), correspondingId);
    }

    /**
     * Creates an immutable code object.
     *
     * @param code            HTTP code code
     * @param correspondingId
     * @throws IllegalArgumentException if the code isn't a valid code code
     */
    public StatusMessage(Code code, MessageId correspondingId) {
        super(correspondingId);
        this.code = requireNonNull(code);
    }

    /**
     * Returns the HTTP status code.
     *
     * @return HTTP status code
     */
    public Code getCode() {
        return code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        StatusMessage that = (StatusMessage) o;
        return code == that.code;
    }

    @Override
    public String toString() {
        return "StatusMessage{" +
                "code=" + code +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id +
                '}';
    }

    public enum Code {
        // -------- 1xx (Informational) --------
        STATUS_CONTINUE(100),
        STATUS_SWITCHING_PROTOCOLS(101),
        // -------- 2xx (Successful) --------
        STATUS_OK(200),
        STATUS_CREATED(201),
        STATUS_ACCEPTED(202),
        STATUS_NON_AUTHORITATIVE_INFORMATION(203),
        STATUS_NO_CONTENT(204),
        STATUS_RESET_CONTENT(205),
        STATUS_PARTIAL_CONTENT(206),
        // -------- 3xx (Redirection) --------
        STATUS_MULTIPLE_CHOICES(300),
        STATUS_MOVED_PERMANENTLY(301),
        STATUS_FOUND(302),
        STATUS_SEE_OTHER(303),
        STATUS_NOT_MODIFIED(304),
        STATUS_USE_PROXY(305),
        STATUS_TEMPORARY_REDIRECT(307),
        // -------- 4xx (Client Error) --------
        STATUS_BAD_REQUEST(400),
        STATUS_UNAUTHORIZED(401),
        STATUS_PAYMENT_REQUIRED(402),
        STATUS_FORBIDDEN(403),
        STATUS_NOT_FOUND(404),
        STATUS_METHOD_NOT_ALLOWED(405),
        STATUS_NOT_ACCEPTABLE(406),
        STATUS_PROXY_AUTHENTICATION_REQUIRED(407),
        STATUS_REQUEST_TIMEOUT(408),
        STATUS_CONFLICT(409),
        STATUS_GONE(410),
        STATUS_LENGTH_REQUIRED(411),
        STATUS_PRECONDITION_FAILED(412),
        STATUS_PAYLOAD_TOO_LARGE(413),
        STATUS_URI_TOO_LONG(414),
        STATUS_UNSUPPORTED_MEDIA_TYPE(415),
        STATUS_RANGE_NOT_SATISFIABLE(416),
        STATUS_EXPECTATION_FAILED(417),
        STATUS_IM_A_TEAPOT(418),
        STATUS_UPGRADE_REQUIRED(426),
        STATUS_INVALID_SIGNATURE(427),
        // -------- 5xx (Server Error) --------
        STATUS_INTERNAL_SERVER_ERROR(500),
        STATUS_NOT_IMPLEMENTED(501),
        STATUS_BAD_GATEWAY(502),
        STATUS_SERVICE_UNAVAILABLE(503),
        STATUS_GATEWAY_TIMEOUT(504),
        STATUS_HTTP_VERSION_NOT_SUPPORTED(505);
        private static final Map<Integer, Code> codes = new HashMap<>();

        static {
            for (Code code : Code.values()) {
                codes.put(code.getNumber(), code);
            }
        }

        private final int number;

        Code(int number) {
            this.number = number;
        }

        @JsonValue
        public int getNumber() {
            return number;
        }

        @JsonCreator
        public static Code from(int code) {
            return codes.get(code);
        }
    }
}
