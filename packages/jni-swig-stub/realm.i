%module(directors="1") realmc

%{
#include "realm.h"
#include <cstring>
#include <string>
#include "realm_api_helpers.h"
%}

// FIXME MEMORY Verify finalizers, etc.
//  https://github.com/realm/realm-kotlin/issues/93
// TODO OPTIMIZATION
//  - Transfer "value semantics" objects in one go. Maybe custom serializer into byte buffers for all value types

%include "typemaps.i"
%include "stdint.i"
%include "arrays_java.i"

// We do not want to use BigInteger for uintt64_t as we are not expecting overflows
%apply int64_t {uint64_t};

// Manual imports in java module class
%pragma(java) moduleimports=%{
%}

// Manual additions to java module class
%pragma(java) modulecode=%{
//  Manual addition
%}

// Helpers included directly in cpp file
%{
realm_string_t rlm_str(const char* str)
{
    return realm_string_t{str, std::strlen(str)};
}
std::string rlm_stdstr(realm_string_t val)
{
    return std::string(val.data, 0, val.size);
}
%}

// Primitive/built in type handling
typedef jstring realm_string_t;
// TODO OPTIMIZATION Optimize...maybe port JStringAccessor from realm-java
//%typemap(jtype) realm_string_t "String"
//%typemap(jstype) realm_string_t "String"
%typemap(in) (realm_string_t) "$1 = rlm_str(jenv->GetStringUTFChars($arg,0));"
%typemap(out) (realm_string_t) "$result = jenv->NewStringUTF(std::string($1.data, 0, $1.size).c_str());"

%typemap(jstype) void* "long"
%typemap(javain) void* "$javainput"
%typemap(javadirectorin) void* "$1"
%typemap(javaout) void* {
return $jnicall;
}

// Reuse above type maps on other pointers too
%apply void* { realm_t*, realm_config_t*, realm_schema_t*, realm_object_t* , realm_query_t*,
               realm_results_t*, realm_notification_token_t*, realm_object_changes_t*,
               realm_list_t*, realm_app_config_t* };

// For all functions returning a pointer or bool, check for null/false and throw an error if
// realm_get_last_error returns true.
// To bypass automatic error checks define the function explicitly here before the type maps until
// we have a distinction (type map, etc.) in the C API that we can use for targeting the type map.
bool realm_object_is_valid(const realm_object_t*);

%typemap(out) SWIGTYPE* {
    if (!result) {
        realm_error_t error;
        if (realm_get_last_error(&error)) {
            std::string message("[" + std::to_string(error.error) + "]: " + error.message);
            realm_clear_last_error();
            // TODO API-SCHEMA Cache class lookup
            // FIXME Extract all error information and throw exceptions based on type
            //  https://github.com/realm/realm-kotlin/issues/70
            jclass clazz = (jenv)->FindClass("java/lang/RuntimeException");
            (jenv)->ThrowNew(clazz, message.c_str());
        }
    }
    *($1_type*)&jresult = result;
}
%typemap(out) bool {
    if (!result) {
        realm_error_t error;
        if (realm_get_last_error(&error)) {
            std::string message("[" + std::to_string(error.error) + "]: " + error.message);
            realm_clear_last_error();
            // TODO API-SCHEMA Cache class lookup
            // FIXME Extract all error information and throw exceptions based on type
            jclass clazz = (jenv)->FindClass("java/lang/RuntimeException");
            (jenv)->ThrowNew(clazz, message.c_str());
        }
    }
    jresult = (jboolean)result;
}
// Just showcasing a wrapping concept. Maybe we should just go with `long` (apply void* as above)
//%typemap(jstype) realm_t* "LongPointerWrapper"
//%typemap(javain) realm_t* "$javainput.ptr()"
//%typemap(javaout) realm_t* {
//    return new LongPointerWrapper($jnicall);
//}

// Array wrappers to allow building (continuous allocated) arrays of the corresponding types from
// JVM
%include "carrays.i"
%array_functions(realm_class_info_t, classArray);
%array_functions(realm_property_info_t, propertyArray);
%array_functions(realm_property_info_t*, propertyArrayArray);
%array_functions(realm_value_t, valueArray);

// size_t output parameter
%inline %{
struct realm_size_t {
    size_t value;
};
%}

// size_t output parameter
// The below struct is used to pass size_t output parameters to Java.
%typemap(jni) (size_t* out_count) "long"
%typemap(jtype) (size_t* out_count) "long"
%typemap(jstype) (size_t* out_count) "realm_size_t"
%typemap(javain) (size_t* out_count) "realm_size_t.getCPtr($javainput)"
// The below type maps are used to convert realm_size_t into a pointer to the same struct in JNI
// The type maps are only applied to arguments are named exactly 'out_count'
%apply size_t* out_count { size_t* out_size };

// bool output parameter
%apply bool* OUTPUT { bool* out_found };

// uint64_t output parameter for realm_get_num_versions
%apply int64_t* OUTPUT { uint64_t* out_versions_count };

// Enable passing uint8_t* parameters for realm_config_get_encryption_key and realm_config_set_encryption_key as Byte[]
%apply int8_t[] {uint8_t *key};
%apply int8_t[] {uint8_t *out_key};

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);

// Make swig types package private (as opposed to public by default) to ensure that we don't expose
// types outside the package
%typemap(javaclassmodifiers) SWIGTYPE "class";
%typemap(javaclassmodifiers) NotificationCallback "public class";
%typemap(javaclassmodifiers) enum SWIGTYPE "final class";

// FIXME OPTIMIZE Support getting/setting multiple attributes. Ignored for now due to incorrect
//  type cast in Swig-generated wrapper for "const realm_property_key_t*" which is not cast
//  correctly to the underlying C-API method.
%ignore "realm_get_values";
%ignore "realm_set_values";
// Not yet available in library
%ignore "realm_config_set_sync_config";
%ignore "realm_update_schema_advanced";
%ignore "realm_config_set_audit_factory";
%ignore "_realm_get_schema_native";
%ignore "realm_find_primary_key_property";
%ignore "_realm_list_from_native_copy";
%ignore "_realm_list_from_native_move";
%ignore "realm_list_assign";
%ignore "_realm_set_from_native_copy";
%ignore "_realm_set_from_native_move";
%ignore "realm_get_set";
%ignore "realm_set_size";
%ignore "realm_set_get";
%ignore "realm_set_find";
%ignore "realm_set_insert";
%ignore "realm_set_erase";
%ignore "realm_set_clear";
%ignore "realm_set_assign";
%ignore "realm_set_add_notification_callback";
%ignore "_realm_dictionary_from_native_copy";
%ignore "_realm_dictionary_from_native_move";
%ignore "realm_get_dictionary";
%ignore "realm_dictionary_size";
%ignore "realm_dictionary_get";
%ignore "realm_dictionary_insert";
%ignore "realm_dictionary_erase";
%ignore "realm_dictionary_clear";
%ignore "realm_dictionary_assign";
%ignore "realm_dictionary_add_notification_callback";
%ignore "realm_query_delete_all";
%ignore "realm_results_snapshot";

// Sync methods not implemented yet
%ignore "realm_sync_config_new";
%ignore "realm_sync_config_set_session_stop_policy";
%ignore "realm_sync_config_set_error_handler";
%ignore "realm_sync_config_set_realm_encryption_key";
%ignore "realm_sync_config_set_client_validate_ssl";
%ignore "realm_sync_config_set_ssl_trust_certificate_path";
%ignore "realm_sync_config_set_ssl_verify_callback";
%ignore "realm_sync_config_set_cancel_waits_on_nonfatal_error";
%ignore "realm_sync_config_set_authorization_header_name";
%ignore "realm_sync_config_set_custom_http_header";
%ignore "realm_sync_config_set_recovery_directory_path";
%ignore "realm_sync_config_set_resync_mode";
%ignore "realm_sync_client_config_new";
%ignore "realm_sync_client_config_set_base_file_path";
%ignore "realm_sync_client_config_set_metadata_mode";
%ignore "realm_sync_client_config_set_encryption_key";
%ignore "realm_sync_client_config_set_reset_metadata_on_error";
%ignore "realm_sync_client_config_set_logger_factory";
%ignore "realm_sync_client_config_set_log_level";
%ignore "realm_sync_client_config_set_reconnect_mode";
%ignore "realm_sync_client_config_set_multiplex_sessions";
%ignore "realm_sync_client_config_set_user_agent_binding_info";
%ignore "realm_sync_client_config_set_user_agent_application_info";
%ignore "realm_sync_client_config_set_connect_timeout";
%ignore "realm_sync_client_config_set_connection_linger_time";
%ignore "realm_sync_client_config_set_ping_keepalive_period";
%ignore "realm_sync_client_config_set_pong_keepalive_period";
%ignore "realm_sync_client_config_set_fast_reconnect_limit";

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "realm.h"
%include "src/main/jni/realm_api_helpers.h"

