#include <rime_api.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
namespace fs = std::filesystem;
void require(bool ok, const std::string &message) {
  if (!ok) throw std::runtime_error(message);
}
// Selects the first candidate of the current segment whose text is exactly `text`.
void selectText(RimeApi *api, RimeSessionId session, const std::string &text) {
  RimeCandidateListIterator iterator{};
  require(api->candidate_list_begin(session, &iterator), "candidate iterator missing");
  int matchedIndex = -1;
  for (int index = 0; index < 500 && api->candidate_list_next(&iterator); ++index) {
    if (std::string(iterator.candidate.text) == text) {
      matchedIndex = index;
      break;
    }
  }
  api->candidate_list_end(&iterator);
  require(matchedIndex >= 0, "missing candidate: " + text);
  require(api->select_candidate(session, matchedIndex), "cannot select candidate: " + text);
}
// Commit count of a single-character entry in the exported user dictionary snapshot.
int snapshotCommits(const fs::path &dir, const std::string &text) {
  for (const auto &file : fs::recursive_directory_iterator(dir / "sync")) {
    if (file.path().filename() != "rime_mint.userdb.txt") continue;
    std::ifstream input(file.path());
    std::string line;
    while (std::getline(input, line)) {
      std::istringstream fields(line);
      std::string code, word, stats;
      if (!std::getline(fields, code, '\t') || !std::getline(fields, word, '\t') ||
          !std::getline(fields, stats) || word != text) continue;
      auto begin = stats.find("c=");
      if (begin != std::string::npos) return std::stoi(stats.substr(begin + 2));
    }
  }
  return 0;
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
    // Picking each character of a phrase one at a time must count the characters too,
    // not only the assembled phrase (fcitx_char_learning.lua).
    api->clear_composition(session);
    api->set_option(session, "transcription", false);
    require(api->simulate_key_sequence(session, "nihao"), "input failed");
    selectText(api, session, "泥");
    selectText(api, session, "浩");
    RIME_STRUCT(RimeCommit, commit);
    require(api->get_commit(session, &commit), "picked characters did not commit");
    const std::string picked = commit.text ? commit.text : "";
    api->free_commit(&commit);
    require(picked == "泥浩", "unexpected commit of picked characters: " + picked);
    api->destroy_session(session);
    // Scheme Lua scripts may keep the user dictionary open until shutdown; export it from
    // a fresh instance so the snapshot can be read.
    api->finalize();
    api->initialize(&traits);
    api->start_maintenance(False);  // loads the deployer tasks used by sync
    api->join_maintenance_thread();
    require(api->sync_user_data(), "cannot export user dictionary");
    api->join_maintenance_thread();
    for (const char *text : {"泥", "浩"}) {
      require(snapshotCommits(dir, text) >= 1,
              std::string("picked character was not learned: ") + text);
    }
    api->finalize();
    std::cout << "All real Mint native OpenCC component, commit and learning cases passed\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << error.what() << std::endl;
    return 1;
  }
}
