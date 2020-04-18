/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.common.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Status that representing a HTTP/1.1 response status code, defined in RFC 7231
 * section 6 (<a href=
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
 * understand the meaning of all registered status codes, though such
 * understanding is obviously desirable. However, a client MUST understand the
 * class of any status code, as indicated by the first digit, and treat an
 * unrecognized status code as being equivalent to the x00 status code of that
 * class, with the exception that a recipient MUST NOT cache a response with an
 * unrecognized status code. </i>
 * </p>
 * 
 * <p>
 * <i> For example, if an unrecognized status code of 471 is received by a
 * client, the client can assume that there was something wrong with its request
 * and treat the response as if it had received a 400 (Bad Request) status code.
 * The response message will usually contain a representation that explains the
 * status. </i>
 * </p>
 * 
 * <p>
 * <i> The first digit of the status-code defines the class of response. The
 * last two digits do not have any categorization role. There are five values
 * for the first digit: </i>
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
 * 
 */
public class Status extends AbstractMessage {
    // -------- constants --------

    // -------- 1xx (Informational) --------
    public static final Status CONTINUE = new Status(100);
    public static final Status SWITCHING_PROTOCOLS = new Status(101);

    // -------- 2xx (Successful) --------
    public static final Status OK = new Status(200);
    public static final Status CREATED = new Status(201);
    public static final Status ACCEPTED = new Status(202);
    public static final Status NON_AUTHORITATIVE_INFORMATION = new Status(203);
    public static final Status NO_CONTENT = new Status(204);
    public static final Status RESET_CONTENT = new Status(205);
    public static final Status PARTIAL_CONTENT = new Status(206);

    // -------- 3xx (Redirection) --------
    public static final Status MULTIPLE_CHOICES = new Status(300);
    public static final Status MOVED_PERMANENTLY = new Status(301);
    public static final Status FOUND = new Status(302);
    public static final Status SEE_OTHER = new Status(303);
    public static final Status NOT_MODIFIED = new Status(304);
    public static final Status USE_PROXY = new Status(305);
    public static final Status TEMPORARY_REDIRECT = new Status(307);

    // -------- 4xx (Client Error) --------
    public static final Status BAD_REQUEST = new Status(400);
    public static final Status UNAUTHORIZED = new Status(401);
    public static final Status PAYMENT_REQUIRED = new Status(402);
    public static final Status FORBIDDEN = new Status(403);
    public static final Status NOT_FOUND = new Status(404);
    public static final Status METHOD_NOT_ALLOWED = new Status(405);
    public static final Status NOT_ACCEPTABLE = new Status(406);
    public static final Status PROXY_AUTHENTICATION_REQUIRED = new Status(407);
    public static final Status REQUEST_TIMEOUT = new Status(408);
    public static final Status CONFLICT = new Status(409);
    public static final Status GONE = new Status(410);
    public static final Status LENGTH_REQUIRED = new Status(411);
    public static final Status PRECONDITION_FAILED = new Status(412);
    public static final Status PAYLOAD_TOO_LARGE = new Status(413);
    public static final Status URI_TOO_LONG = new Status(414);
    public static final Status UNSUPPORTED_MEDIA_TYPE = new Status(415);
    public static final Status RANGE_NOT_SATISFIABLE = new Status(416);
    public static final Status EXPECTATION_FAILED = new Status(417);
    public static final Status UPGRADE_REQUIRED = new Status(426);

    // -------- 5xx (Server Error) --------
    public static final Status INTERNAL_SERVER_ERROR = new Status(500);
    public static final Status NOT_IMPLEMENTED = new Status(501);
    public static final Status BAD_GATEWAY = new Status(502);
    public static final Status SERVICE_UNAVAILABLE = new Status(503);
    public static final Status GATEWAY_TIMEOUT = new Status(504);
    public static final Status HTTP_VERSION_NOT_SUPPORTED = new Status(505);

    // -------- attributes --------
    @JsonProperty("status")
    private final short statusCode;

    protected Status() {
        statusCode = 0;
    }

    // -------- methods --------

    /**
     * Creates an immutable status object.
     * 
     * @param status HTTP status code
     * @throws IllegalArgumentException if the status isn't a valid status code
     */
    public Status(int status) {
        if (status < 0 || status > 999)
            throw new IllegalArgumentException("The status-code element must be a positive three-digit integer.");
        this.statusCode = (short) status;
    }

    /**
     * Returns the HTTP status code.
     * 
     * @return HTTP status code
     */
    public int getStatus() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "Status [statusCode=" + statusCode + ", messageID=" + getMessageID() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Status)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Status status = (Status) o;
        return statusCode == status.statusCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), statusCode);
    }
}
