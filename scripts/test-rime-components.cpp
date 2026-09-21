#include <rime_api.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
namespace fs = std::filesystem;
void require(bool ok, const std::string &message) {
  if (!ok) throw std::runtime_error(message);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: test-rime-components RIME_USER_DIR");
    fs::path dir(argv[1]);
    require(fs::exists(dir / "rime_mint.custom.yaml"), "production installer fixture missing");
    // Use the full upstream Mint schema, dictionaries, Lua filters and emoji chain.
    std::ofstream(dir / "default.custom.yaml") << "patch:\n  schema_list:\n    - schema: rime_mint\n";
    auto api = rime_get_api();
    RIME_STRUCT(RimeTraits, traits);
    auto path = dir.string();
    const char *modules[] = {"default", "plugins", "lua", nullptr};
    traits.shared_data_dir = path.c_str();
    traits.user_data_dir = path.c_str();
    traits.app_name = "rime.component-test";
    traits.modules = modules;
    traits.log_dir = path.c_str();
    api->setup(&traits);
    api->initialize(&traits);
    api->start_maintenance(True);
    api->join_maintenance_thread();

    auto session = api->create_session();
    require(session != 0 && api->select_schema(session, "rime_mint"), "cannot select real Mint schema");
    require(api->get_option(session, "_fcitx_components"), "native component filter not enabled");
    struct Case { const char *input, *text, *component; bool traditional = false; };
    for (const auto &test : {Case{"ya", "呀", "口"}, {"gan", "咁", "口"},
                            {"z", "中", "口"}, {"zhi", "只", "口"},
                            {"zhao", "找", "扌"}, {"he", "河", "氵"},
                            {"yu", "語", "訁", true}}) {
      api->clear_composition(session);
      api->set_option(session, "transcription", test.traditional);
      require(api->simulate_key_sequence(session, test.input), "input failed");
      RimeCandidateListIterator iterator{};
      require(api->candidate_list_begin(session, &iterator), "candidate iterator missing");
      int matchedIndex = -1;
      int index = 0;
      while (index < 500 && api->candidate_list_next(&iterator)) {
        const auto &c = iterator.candidate;
        if (std::string(c.text) == test.text) {
          std::string comment = c.comment ? c.comment : "";
          const std::string marker = "\u2063fcitx-radical:";
          auto begin = comment.find(marker);
          auto end = begin == std::string::npos ? begin : comment.find("\u2063", begin + marker.size());
          if (begin != std::string::npos && end != std::string::npos &&
              comment.substr(begin + marker.size(), end - begin - marker.size()).find(test.component) != std::string::npos)
            matchedIndex = index;
          std::cout << test.text << ": " << comment << std::endl;
          break;
        }
        ++index;
      }
      api->candidate_list_end(&iterator);
      require(matchedIndex >= 0, std::string("missing component in real Mint candidate: ") + test.text);
      require(api->select_candidate(session, matchedIndex), "cannot select filtered candidate");
      RIME_STRUCT(RimeCommit, commit);
      require(api->get_commit(session, &commit), "candidate did not commit");
      const std::string committed = commit.text ? commit.text : "";
      api->free_commit(&commit);
      require(committed == test.text, "component filter changed committed text");
    }
    api->destroy_session(session);
    api->finalize();
    std::cout << "All real Mint native OpenCC component and commit cases passed\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << error.what() << std::endl;
    return 1;
  }
}
