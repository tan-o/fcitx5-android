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
    require(argc == 3, "usage: test-rime-components ASSETS TEMP_DIR");
    fs::path assets(argv[1]), dir(argv[2]);
    fs::create_directories(dir / "lua");
    for (auto name : {"fcitx_components.dict.yaml", "fcitx_components.schema.yaml"})
      fs::copy_file(assets / name, dir / name);
    fs::copy_file(assets / "lua/fcitx_radical_filter.lua", dir / "lua/fcitx_radical_filter.lua");
    std::ofstream(dir / "default.yaml") << "config_version: '1'\nschema_list:\n  - schema: fixture\n";
    std::ofstream(dir / "fixture.dict.yaml") <<
      "---\nname: fixture\nversion: '1'\nsort: original\n...\n"
      "呀\tya\n中\tzhong\n只\tzhi\n找\tzhao\n河\the\n語\tyu\n";
    std::ofstream(dir / "fixture.schema.yaml") <<
      "schema:\n  schema_id: fixture\n  name: Fixture\n  version: '1'\n"
      "  dependencies: [fcitx_components]\n"
      "engine:\n  processors: [speller, selector, express_editor]\n"
      "  segmentors: [abc_segmentor]\n  translators: [table_translator]\n"
      "  filters: [lua_filter@*fcitx_radical_filter]\n"
      "speller:\n  alphabet: abcdefghijklmnopqrstuvwxyz\n"
      "translator:\n  dictionary: fixture\n  enable_user_dict: false\n";
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
    require(fs::exists(dir / "build/fcitx_components.reverse.bin"), "reverse dictionary missing");
    auto session = api->create_session();
    require(session != 0 && api->select_schema(session, "fixture"), "cannot select fixture");
    struct Case { const char *input, *text, *component; };
    for (const auto &test : {Case{"ya", "呀", "口"}, {"zhong", "中", "口"},
                            {"zhi", "只", "口"}, {"zhao", "找", "扌"},
                            {"he", "河", "氵"}, {"yu", "語", "言"}}) {
      api->clear_composition(session);
      require(api->simulate_key_sequence(session, test.input), "input failed");
      RIME_STRUCT(RimeContext, context);
      require(api->get_context(session, &context), "context missing");
      bool matched = false;
      for (int i = 0; i < context.menu.num_candidates; ++i) {
        const auto &c = context.menu.candidates[i];
        if (std::string(c.text) != test.text) continue;
        std::string comment = c.comment ? c.comment : "";
        const std::string marker = "\u2063fcitx-radical:";
        auto begin = comment.find(marker);
        auto end = begin == std::string::npos ? begin : comment.find("\u2063", begin + marker.size());
        if (begin != std::string::npos && end != std::string::npos)
          matched = comment.substr(begin + marker.size(), end - begin - marker.size()).find(test.component) != std::string::npos;
        std::cout << test.text << ": " << comment << std::endl;
      }
      api->free_context(&context);
      require(matched, std::string("missing component for ") + test.text);
    }
    api->destroy_session(session);
    api->finalize();
    std::cout << "All real Rime reverse lookup/filter cases passed\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << error.what() << std::endl;
    return 1;
  }
}

