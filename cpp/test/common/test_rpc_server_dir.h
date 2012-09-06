/*
 * Copyright (c) 2012 by Michael Berlin, Zuse Institute Berlin
 *
 * Licensed under the BSD License, see LICENSE file for details.
 *
 */

#ifndef CPP_TEST_COMMON_TEST_RPC_SERVER_DIR_CPP_
#define CPP_TEST_COMMON_TEST_RPC_SERVER_DIR_CPP_

#include "common/test_rpc_server.h"

#include <boost/thread/mutex.hpp>
#include <map>
#include <string>

namespace google {
namespace protobuf {
class Message;
}  // namespace protobuf
}  // namespace google

namespace xtreemfs {
namespace rpc {

class TestRPCServerDIR : public TestRPCServer<TestRPCServerDIR> {
 public:
  TestRPCServerDIR();

  void RegisterVolume(const std::string& volume_name,
                      const std::string& mrc_uuid);

 private:
  google::protobuf::Message* GetServiceByNameOperation(
      const pbrpc::Auth& auth,
      const pbrpc::UserCredentials& user_credentials,
      const google::protobuf::Message& request,
      const char* data,
      boost::uint32_t data_len);

  google::protobuf::Message* GetServiceByUUIDOperation(
      const pbrpc::Auth& auth,
      const pbrpc::UserCredentials& user_credentials,
      const google::protobuf::Message& request,
      const char* data,
      boost::uint32_t data_len);

  google::protobuf::Message* GetAddressMappingOperation(
      const pbrpc::Auth& auth,
      const pbrpc::UserCredentials& user_credentials,
      const google::protobuf::Message& request,
      const char* data,
      boost::uint32_t data_len);

  /** Guards access to known_volumes_. */
  boost::mutex mutex_;

  std::map<std::string, std::string> known_volumes_;
};

}  // namespace rpc
}  // namespace xtreemfs

#endif  // CPP_TEST_COMMON_TEST_RPC_SERVER_DIR_CPP_