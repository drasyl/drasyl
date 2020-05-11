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
package org.drasyl.core.common.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.StatusMessageAction;

import java.util.Objects;

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
public class StatusMessage extends AbstractMessage<StatusMessage> {
    // -------- constants --------
    // -------- 1xx (Informational) --------
    public static final StatusMessage CONTINUE = new StatusMessage(100);
    public static final StatusMessage SWITCHING_PROTOCOLS = new StatusMessage(101);
    // -------- 2xx (Successful) --------
    public static final StatusMessage OK = new StatusMessage(200);
    public static final StatusMessage CREATED = new StatusMessage(201);
    public static final StatusMessage ACCEPTED = new StatusMessage(202);
    public static final StatusMessage NON_AUTHORITATIVE_INFORMATION = new StatusMessage(203);
    public static final StatusMessage NO_CONTENT = new StatusMessage(204);
    public static final StatusMessage RESET_CONTENT = new StatusMessage(205);
    public static final StatusMessage PARTIAL_CONTENT = new StatusMessage(206);
    // -------- 3xx (Redirection) --------
    public static final StatusMessage MULTIPLE_CHOICES = new StatusMessage(300);
    public static final StatusMessage MOVED_PERMANENTLY = new StatusMessage(301);
    public static final StatusMessage FOUND = new StatusMessage(302);
    public static final StatusMessage SEE_OTHER = new StatusMessage(303);
    public static final StatusMessage NOT_MODIFIED = new StatusMessage(304);
    public static final StatusMessage USE_PROXY = new StatusMessage(305);
    public static final StatusMessage TEMPORARY_REDIRECT = new StatusMessage(307);
    // -------- 4xx (Client Error) --------
    public static final StatusMessage BAD_REQUEST = new StatusMessage(400);
    public static final StatusMessage UNAUTHORIZED = new StatusMessage(401);
    public static final StatusMessage PAYMENT_REQUIRED = new StatusMessage(402);
    public static final StatusMessage FORBIDDEN = new StatusMessage(403);
    public static final StatusMessage NOT_FOUND = new StatusMessage(404);
    public static final StatusMessage METHOD_NOT_ALLOWED = new StatusMessage(405);
    public static final StatusMessage NOT_ACCEPTABLE = new StatusMessage(406);
    public static final StatusMessage PROXY_AUTHENTICATION_REQUIRED = new StatusMessage(407);
    public static final StatusMessage REQUEST_TIMEOUT = new StatusMessage(408);
    public static final StatusMessage CONFLICT = new StatusMessage(409);
    public static final StatusMessage GONE = new StatusMessage(410);
    public static final StatusMessage LENGTH_REQUIRED = new StatusMessage(411);
    public static final StatusMessage PRECONDITION_FAILED = new StatusMessage(412);
    public static final StatusMessage PAYLOAD_TOO_LARGE = new StatusMessage(413);
    public static final StatusMessage URI_TOO_LONG = new StatusMessage(414);
    public static final StatusMessage UNSUPPORTED_MEDIA_TYPE = new StatusMessage(415);
    public static final StatusMessage RANGE_NOT_SATISFIABLE = new StatusMessage(416);
    public static final StatusMessage EXPECTATION_FAILED = new StatusMessage(417);
    public static final StatusMessage UPGRADE_REQUIRED = new StatusMessage(426);
    // -------- 5xx (Server Error) --------
    public static final StatusMessage INTERNAL_SERVER_ERROR = new StatusMessage(500);
    public static final StatusMessage NOT_IMPLEMENTED = new StatusMessage(501);
    public static final StatusMessage BAD_GATEWAY = new StatusMessage(502);
    public static final StatusMessage SERVICE_UNAVAILABLE = new StatusMessage(503);
    public static final StatusMessage GATEWAY_TIMEOUT = new StatusMessage(504);
    public static final StatusMessage HTTP_VERSION_NOT_SUPPORTED = new StatusMessage(505);
    // -------- attributes --------
    @JsonProperty("status")
    private final short statusCode;

    protected StatusMessage() {
        statusCode = 0;
    }

    // -------- methods --------

    /**
     * Creates an immutable status object.
     *
     * @param status HTTP status code
     * @throws IllegalArgumentException if the status isn't a valid status code
     */
    public StatusMessage(int status) {
        if (status < 0 || status > 999) {
            throw new IllegalArgumentException("The status-code element must be a positive three-digit integer.");
        }
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
    public MessageAction<StatusMessage> getAction() {
        return new StatusMessageAction(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), statusCode);
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
        StatusMessage status = (StatusMessage) o;
        return statusCode == status.statusCode;
    }

    @Override
    public String toString() {
        return "StatusMessage{" +
                "statusCode=" + statusCode +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
