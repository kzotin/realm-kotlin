/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.internal.interop.sync

/**
 * Wrapper for C-API `realm_app_errno_service`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2559
 */
expect enum class ServiceErrorCode : CodeDescription {
    RLM_APP_ERR_SERVICE_MISSING_AUTH_REQ,
    RLM_APP_ERR_SERVICE_INVALID_SESSION,
    RLM_APP_ERR_SERVICE_USER_APP_DOMAIN_MISMATCH,
    RLM_APP_ERR_SERVICE_DOMAIN_NOT_ALLOWED,
    RLM_APP_ERR_SERVICE_READ_SIZE_LIMIT_EXCEEDED,
    RLM_APP_ERR_SERVICE_INVALID_PARAMETER,
    RLM_APP_ERR_SERVICE_MISSING_PARAMETER,
    RLM_APP_ERR_SERVICE_TWILIO_ERROR,
    RLM_APP_ERR_SERVICE_GCM_ERROR,
    RLM_APP_ERR_SERVICE_HTTP_ERROR,
    RLM_APP_ERR_SERVICE_AWS_ERROR,
    RLM_APP_ERR_SERVICE_MONGODB_ERROR,
    RLM_APP_ERR_SERVICE_ARGUMENTS_NOT_ALLOWED,
    RLM_APP_ERR_SERVICE_FUNCTION_EXECUTION_ERROR,
    RLM_APP_ERR_SERVICE_NO_MATCHING_RULE_FOUND,
    RLM_APP_ERR_SERVICE_INTERNAL_SERVER_ERROR,
    RLM_APP_ERR_SERVICE_AUTH_PROVIDER_NOT_FOUND,
    RLM_APP_ERR_SERVICE_AUTH_PROVIDER_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_SERVICE_NOT_FOUND,
    RLM_APP_ERR_SERVICE_SERVICE_TYPE_NOT_FOUND,
    RLM_APP_ERR_SERVICE_SERVICE_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_SERVICE_COMMAND_NOT_FOUND,
    RLM_APP_ERR_SERVICE_VALUE_NOT_FOUND,
    RLM_APP_ERR_SERVICE_VALUE_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_VALUE_DUPLICATE_NAME,
    RLM_APP_ERR_SERVICE_FUNCTION_NOT_FOUND,
    RLM_APP_ERR_SERVICE_FUNCTION_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_FUNCTION_DUPLICATE_NAME,
    RLM_APP_ERR_SERVICE_FUNCTION_SYNTAX_ERROR,
    RLM_APP_ERR_SERVICE_FUNCTION_INVALID,
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_NOT_FOUND,
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_DUPLICATE_NAME,
    RLM_APP_ERR_SERVICE_RULE_NOT_FOUND,
    RLM_APP_ERR_SERVICE_API_KEY_NOT_FOUND,
    RLM_APP_ERR_SERVICE_RULE_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_RULE_DUPLICATE_NAME,
    RLM_APP_ERR_SERVICE_AUTH_PROVIDER_DUPLICATE_NAME,
    RLM_APP_ERR_SERVICE_RESTRICTED_HOST,
    RLM_APP_ERR_SERVICE_API_KEY_ALREADY_EXISTS,
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_AUTH_FAILED,
    RLM_APP_ERR_SERVICE_EXECUTION_TIME_LIMIT_EXCEEDED,
    RLM_APP_ERR_SERVICE_NOT_CALLABLE,
    RLM_APP_ERR_SERVICE_USER_ALREADY_CONFIRMED,
    RLM_APP_ERR_SERVICE_USER_NOT_FOUND,
    RLM_APP_ERR_SERVICE_USER_DISABLED,
    RLM_APP_ERR_SERVICE_AUTH_ERROR,
    RLM_APP_ERR_SERVICE_BAD_REQUEST,
    RLM_APP_ERR_SERVICE_ACCOUNT_NAME_IN_USE,
    RLM_APP_ERR_SERVICE_INVALID_EMAIL_PASSWORD,
    RLM_APP_ERR_SERVICE_UNKNOWN,
    RLM_APP_ERR_SERVICE_NONE;

    companion object {
        internal fun of(nativeValue: Int): ServiceErrorCode?
    }
}
