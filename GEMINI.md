# PROJECT HANDOFF & SYSTEM ARCHITECTURE: AntiArmorTrim (v2.6.0 - Config v6)

> **TÀI LIỆU HỆ THỐNG BẤT BIẾN DÀNH CHO AI AGENT (GOOGLE ANTIGRAVITY):**
> Tài liệu này định nghĩa toàn bộ kiến trúc, cơ sở dữ liệu SQLite, luồng xử lý sự kiện, quy tắc Typography/UI và các bản vá kỹ thuật chuyên sâu của dự án **AntiArmorTrim (`VCTrimRemover`)**. Mọi chỉnh sửa hoặc nâng cấp trong tương lai BẮT BUỘC phải tuân thủ nghiêm ngặt các quy tắc trong tài liệu này.

---

## 1. TỔNG QUAN DỰ ÁN & MÔI TRƯỜNG (PROJECT & ENVIRONMENT)

- **Tên Plugin:** `AntiArmorTrim` (Repo: `VCTrimRemover`)
- **Phiên bản:** `2.6.0` (`config-version: 6` với cơ chế tự động migrate config `syncConfigWithDefaults()`)
- **Nền tảng Server:** Paper / Leaf / AdvancedSlimePaper `1.21.5` – `1.21.11` (`NMS v1_21_R11`, `api-version: '1.21'`)
- **Ngôn ngữ & Build Tool:** Java 21 / Java 25 (Target bytecode JDK 21), biên dịch qua Maven: `mvn clean package --batch-mode`
- **Package gốc cố định:** `com.server.antitrim`
- **Soft-Dependencies:** `LuckPerms`, `Nexo` (v1.26+), `ModelEngine` (R4.x), `Essentials`, `PerPlayerKit`

---

## 2. CÂY THƯ MỤC CHUẨN MODULAR (EXACT FILE STRUCTURE)

Dự án gồm 18 file thành phần được phân tầng rõ ràng:

```text
VCTrimRemover/
├── GEMINI.md                                               (File System Prompt & Handoff cho Antigravity)
├── pom.xml                                                 (Cấu hình Maven, Paper API 1.21.1, LuckPerms, SQLite)
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── server/
        │           └── antitrim/
        │               ├── AntiArmorTrim.java              (Main class, SQLite DB, Sync Config v6, Nexo Reflection, Parsers, Commands)
        │               ├── CombatTracker.java              (Theo dõi sát thương, Tap-shot TNT Minecart & White Death Message + Hover)
        │               ├── TrimListener.java               (Khóa ngày/thời tiết, chặn Trim, tương thích Kit & quét xóa Rename trái phép)
        │               ├── manager/
        │               │   ├── HatManager.java             (Quản lý mũ Nexo, bảo toàn chỉ số giáp/độ bền, ẩn Tooltip & chống flicker)
        │               │   ├── KillEffectManager.java      (Hook ModelEngine 4, spawn 3D model, copy skin PlayerLimb, particles & sound)
        │               │   ├── KillSoundManager.java       (Đọc sounds.yml của Nexo, quản lý quyền & phát âm thanh kết liễu)
        │               │   └── RenameManager.java          (Đổi tên vật phẩm, đọc signedMessage, gắn PDC custom_renamed & kiểm tra quyền)
        │               └── gui/
        │                   ├── TrimMenuHolder.java         (InventoryHolder, MenuType enum, Shared UI Builders, Locked Lore Template)
        │                   ├── DecorHubGui.java            (Menu trung tâm /decor với 5 công cụ thẩm mỹ cân đối đối xứng)
        │                   ├── TrimStudioGui.java          (Menu /trim chọn 18 hoa văn & 10 chất liệu + GRAY_DYE khi khóa)
        │                   ├── HatStudioGui.java           (Menu /hat cho Player với GRAY_DYE khi khóa + Menu Admin & Perm Groups)
        │                   ├── KillEffectHubGui.java       (Menu trung tâm /ke chọn Classic Particle hoặc Modern 3D KillEffect)
        │                   ├── KillEffectStudioGui.java    (Menu /ke 3d cho Player với GRAY_DYE khi khóa + Menu Admin & Perm Groups)
        │                   ├── KillSoundStudioGui.java     (Menu /ks cho Player với GRAY_DYE khi khóa + Menu Admin & Perm Groups)
        │                   └── RenameStudioGui.java        (Menu /rename kèm sách hướng dẫn mã màu & preview vật phẩm trên tay)
        └── resources/
            ├── config.yml                                  (Cấu hình config-version: 6, tin nhắn, GUI titles, âm lượng, placeholder)
            └── plugin.yml                                  (Đăng ký commands, aliases, permissions & softdepend)

3. KIẾN TRÚC CƠ SỞ DỮ LIỆU SQLITE (plugins/AntiArmorTrim/database.db)

    Kết nối JDBC: jdbc:sqlite:plugins/AntiArmorTrim/database.db

    Cơ chế Cache: Toàn bộ dữ liệu từ SQLite được nạp vào các cấu trúc dữ liệu In-Memory (dbHatItems, dbHatNames, dbHatLores, dbPermGroups, dbKillEffectItems, dbKillEffectGroups, dbKillSoundItems, dbKillSoundGroups) khi bật plugin hoặc gõ /at reload để truy xuất 0ms trên Main Thread. Mọi thao tác chỉnh sửa của Admin trong GUI sẽ ghi thẳng xuống SQLite và cập nhật Cache ngay lập tức.

    7 Bảng dữ liệu (Tables):

        hat_items (hat_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)

        hat_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)

            Lưu các khóa: default_hat, force_default_hat, default_killeffect, force_default_killeffect, default_killsound, force_default_killsound.

        hat_groups (perm_node TEXT PRIMARY KEY, hat_ids TEXT) (Danh sách ID ngăn cách bởi dấu phẩy ,).

        killeffect_items (effect_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)

            Tự động seed 10 hiệu ứng mặc định của bộ EC_KillEffects_v4 nếu bảng trống khi khởi tạo.

        killeffect_groups (perm_node TEXT PRIMARY KEY, effect_ids TEXT)

        killsound_items (sound_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)

        killsound_groups (perm_node TEXT PRIMARY KEY, sound_ids TEXT)

4. ĐẶC TẢ KỸ THUẬT CHI TIẾT TỪNG MODULE (DETAILED MODULE SPECS)
4.1. Hệ Thống Fast Prefix Editing & Typography Chuẩn (AntiArmorTrim.java)

    Kiến trúc Fast Prefix Editing (Cấu hình một nơi, áp dụng toàn bộ):
        - Mục `prefixes:` đặt ngay đầu file `config.yml` với các khóa: `main`, `trim`, `hat`, `killeffect`, `killsound`, `rename`, `admin`, `system`, `error`.
        - Sử dụng Nexo Glyph `<white><glyph:warning_purple></white>`, `<white><glyph:warning_blue></white>`, và `<white><glyph:warning_red></white>` làm icon prefix chuẩn cho toàn bộ danh mục.
        - Mọi tin nhắn trong `messages:` sử dụng placeholder `<prefix>` hoặc `<prefix:category>`. Hàm `getMessageWithPerm()` tự động dò danh mục tin nhắn qua `getCategoryForMessageKey(key)` và nạp đúng prefix tương ứng thông qua `createPrefixTagResolver()`.
        - **QUY TẮC TYPOGRAPHY BẤT DI BẤT DỊCH CHO CHAT MESSAGES:** Vì `prefixes:` đã sử dụng Nexo `<glyph:warning_...>`, phần nội dung tin nhắn `messages:` đằng sau TUYỆT ĐỐI KHÔNG được chứa 2D Item Text Component (`<sprite:items:item/...>`, `<pattern_icon>`, `<material_icon>`, `<slot_icon>`), bởi việc trộn lẫn Nexo Font Glyph và Item Atlas Sprite trên cùng một dòng chat sẽ làm lệch baseline và hỏng hoàn toàn bố cục Typography. Thay vào đó, trong `messages:` chỉ kết hợp **Minecraft Symbols** (`✔`, `✖`, `✦`, `★`, `✎`, `♪`, `⚠`, `▸`, `▪`) + **MiniText** (`ᴀʙᴄᴅᴇꜰ...` cho từ mô tả/nhãn) + **Normal MC Text** (cho tên riêng, ID, lệnh, số liệu, Rank).
        - **QUY TẮC ĐỒNG BỘ TYPOGRAPHY CHO CÁC DÒNG CÓ CHỨA SỐ (NUMBERS & DIGITS):** Trong font mặc định của Minecraft, các chữ số (`0-9`) hiển thị ở kích thước đầy đủ (full height) và không có chữ số Small Caps tiêu chuẩn. Do đó, trên bất kỳ dòng tin nhắn chat (`messages`) hoặc dòng mô tả (`lore`) nào có chứa số liệu hoặc chữ số (ví dụ: `3D`, `All 4 Armor Pieces`, `18 Patterns`, `10 Materials`, `&0-&f`, `&#rrggbb`, `max <max> chars`, số lượng `.size()`), **TUYỆT ĐỐI KHÔNG dùng MiniText (`ᴀʙᴄ...`) trên cùng dòng đó** mà phải dùng **Normal MC Text** cho toàn bộ từ ngữ trên dòng đó để chiều cao chữ và baseline đồng bộ hoàn toàn, tránh hiện tượng số to lấn át chữ nhỏ gây lệch dòng.

    Phân tách rõ ràng giữa Title/Name và Message/Lore trong GUI:
        - GUI Titles (`gui.*-title`) & Tên vật phẩm/nút bấm (`displayName`): Chỉ sử dụng Normal MC Text + Valid 2D Object Text Component (`<white><sprite:items:item/...></white>`) hoặc Nexo Navigation/Status Glyphs (`<white><glyph:next></white>`, `<white><glyph:previous></white>`, `<white><glyph:cancel></white>`, `<white><glyph:locked_red></white>`). TUYỆT ĐỐI KHÔNG dùng Tiny Text (Small Caps) ở tiêu đề GUI hoặc tên nút bấm vì sẽ gây khó đọc.
        - Lore vật phẩm trong GUI: Thiết kế kết hợp giữa Valid 2D Object Text Component (`<white><sprite:items:item/...></white>`), Nexo Glyph (`<white><glyph:locked_red></white>`), Minecraft Symbols (`✔`, `✖`, `✦`, `★`, `✎`, `♪`, `▸`, `▪`), MiniText (`ᴀʙᴄᴅᴇꜰ...` cho nhãn/tags/mô tả phụ) và Normal MC Text (cho tên riêng, ID, lệnh, số liệu, Rank).
        - Giao diện GUI Nút bấm điều hướng (`TrimMenuHolder.java`): Sử dụng Nexo Glyphs `<white><glyph:next></white>` (Next Page), `<white><glyph:previous></white>` (Previous Page / Back), `<white><glyph:cancel></white>` (Reset / Remove / Delete / Close) và `<white><glyph:locked_red></white>` (Locked Item Title & Lore). Hàm `createStyledButton` kiểm tra `!titleMini.contains("<sprite:") && !titleMini.contains("<glyph:")` để không bao giờ chèn chồng sprite vào nút đã có glyph.

    Bộ TagResolver Nexo Glyph chống ám màu & méo font (`createGlyphTagResolver()`):
        - Hàm `createGlyphTagResolver()` và `deserializeNexoGlyph(String rawTag)` gọi qua Reflection tới `com.nexomc.nexo.utils.AdventureUtils` bằng ClassLoader của Nexo, đồng thời ép buộc `NamedTextColor.WHITE` và tắt toàn bộ `TextDecoration` (`BOLD`, `ITALIC`, `UNDERLINED`, `STRIKETHROUGH`, `OBFUSCATED` = `FALSE`) trực tiếp trên Component glyph để ngăn các thẻ màu hoặc `<bold>` phía trước làm ám màu hoặc biến dạng bitmap glyph của Nexo. Có sẵn bộ Unicode symbol fallback an toàn nếu Nexo chưa nạp xong.

    Bộ ánh xạ 2D Sprite chống Error Block (getItemSpriteTag(Material)):

        Trong Minecraft 1.21+, atlas items chỉ chứa texture của Item 2D. Nếu truyền tên một khối Block 3D hoặc Item có texture động vào <sprite:items:item/...>, client sẽ hiển thị khối lỗi màu đen/tím (Missing Texture Error Block).

        Hàm getItemSpriteTag(Material) bắt buộc ánh xạ các Material đặc biệt sang texture 2D hợp lệ:

            COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK

                    
            →
            →

                  

            "comparator"

            BOOKSHELF, CHISELED_BOOKSHELF

                    
            →
            →

                  

            "book"

            ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL

                    
            →
            →

                  

            "iron_ingot"

            JUKEBOX, NOTE_BLOCK

                    
            →
            →

                  

            "music_disc_otherside"

            SMITHING_TABLE

                    
            →
            →

                  

            "silence_armor_trim_smithing_template"

            GRINDSTONE

                    
            →
            →

                  

            "flint"

            CLOCK

                    
            →
            →

                  

            "clock_00"

            COMPASS

                    
            →
            →

                  

            "compass_00"

            RECOVERY_COMPASS

                    
            →
            →

                  

            "recovery_compass_00"

            BLUE_ICE, PACKED_ICE, ICE

                    
            →
            →

                  

            "snowball"

            LODESTONE

                    
            →
            →

                  

            "netherite_ingot"

4.2. Cơ Chế Hiển Thị Mục Bị Khóa Bằng Icon GRAY_DYE & Glyph `<glyph:locked_red>`

    Cấu hình mặc định: hide-locked-hats: false, hide-locked-killeffects: false, hide-locked-killsounds: false.

    Khi người chơi mở /hat, /ke, /ks, hoặc /trim:

        Hiển thị đầy đủ 100% danh sách để người chơi biết server có những vật phẩm nào.

        Nếu có quyền (unlocked == true): Hiển thị icon thật của vật phẩm, cho phép click để trang bị.

        Nếu KHÔNG có quyền (unlocked == false):

            Thay thế icon hiển thị bằng Material.GRAY_DYE (createCleanIconStack(Material.GRAY_DYE)).

            Tên hiển thị: <white><glyph:locked_red></white> <Tên Vật Phẩm> <#6c7086>• <#f38ba8>[Locked].

            Lore hiển thị no-permission-lore:

                <white><glyph:locked_red></white> <#f38ba8><bold>ʟᴏᴄᴋᴇᴅ</bold> <#6c7086>▸ <#f38ba8>ᴄᴀɴɴᴏᴛ ѕᴇʟᴇᴄᴛ <#6c7086>(ɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ)

                <white><sprite:items:item/name_tag></white> <#a6adc8>ʀᴇꞯᴜɪʀᴇᴅ ʀᴀɴᴋ <#6c7086>▸ <rank> (Tự động dò prefix của Group có quyền đó trong LuckPerms).

            Khi click chọn: Phát âm thanh Sound.ENTITY_VILLAGER_NO và gửi tin nhắn no-permission-gui.

4.3. Hệ Thống Decor Hub (/decor - DecorHubGui.java)

    Giao diện 27 ô (3 hàng), viền GRAY_STAINED_GLASS_PANE, bố trí 5 nút cân đối đối xứng ở hàng giữa (ô 9, 10 ở bên trái và ô 16, 17 ở bên phải là kính xám):

        Ô 11: Armor Trim Studio (SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE) → Mở TrimStudioGui.

        Ô 12: Custom Hat Studio (NETHERITE_HELMET) → Mở HatStudioGui.

        Ô 13: KillEffect Studio Hub (TOTEM_OF_UNDYING) → Mở KillEffectHubGui (Menu chọn Classic Particle hoặc Modern 3D).

        Ô 14: KillSound Studio (MUSIC_DISC_OTHERSIDE) → Mở KillSoundStudioGui.

        Ô 15: Item Rename Studio (NAME_TAG) → Mở RenameStudioGui.

4.4. Hệ Thống Custom Hat & Đồng Bộ Độ Bền Chuẩn Giáp Gốc (HatManager.java & HatStudioGui.java)

    Bảo toàn mũ gốc 100%: Khi biến mũ DIAMOND_HELMET hoặc NETHERITE_HELMET thành mũ 3D Nexo:

        Xóa Trim tạm thời trên bản clone của mũ gốc, serialize toàn bộ ItemStack gốc thành byte[] (cleanBase.serializeAsBytes()) và lưu vào PDC key antitrim:original_helmet_bytes.

        Gán lại chỉ số chuẩn 1.21 cho mũ Nexo ở EquipmentSlotGroup.HEAD:

            GENERIC_ARMOR: +3.0

            GENERIC_ARMOR_TOUGHNESS: +2.0 (Diamond) hoặc +3.0 (Netherite)

            GENERIC_KNOCKBACK_RESISTANCE: +0.1 (Netherite)

        Đồng bộ toàn bộ Enchantments (Protection, Unbreaking, Thorns, Respiration, Aqua Affinity...) và thuộc tính Unbreakable từ mũ gốc sang mũ Nexo.

        Gán EquippableComponent cho slot HEAD với setDamageOnHurt(false). Khóa Equippable không tự tính độ bền theo tag tool/vật phẩm phụ.

        Lưu trữ và đồng bộ trạng thái độ bền vào PersistentDataContainer (PDC):

            antitrim:helmet_current_damage (INTEGER): Lưu số điểm sát thương độ bền hiện tại của mũ.

            antitrim:helmet_max_durability (INTEGER): Lưu độ bền tối đa của mũ gốc (Diamond: 363, Netherite: 407).

            antitrim:helmet_base_material (STRING): Lưu loại giáp gốc ("DIAMOND_HELMET" hoặc "NETHERITE_HELMET").

        Xóa toàn bộ PDC key có namespace chứa "nexo" hoặc "oraxen" trên mũ đã trang bị để Nexo không tự động reset lại chỉ số khi người chơi đổi world.

        Dùng Paper DataComponent TOOLTIP_DISPLAY (io.papermc.paper.datacomponent.item.TooltipDisplay\u0024Builder) ẩn toàn bộ dòng tooltip mặc định thừa.

    Cơ Chế Khắc Phục Lỗi Mũ Nexo Chậm Hỏng / Lâu Hỏng Hơn Mũ Thường (Dù Có Enchant Hay Không):

        Nguyên nhân gốc rễ: Nexo sử dụng các Material không phải giáp (như PAPER, CARVED_PUMPKIN...) để hiển thị CustomModelData mà không bị texture 2D của mũ mặc định che mất. Vì vậy, hệ thống unbreaking.json của Minecraft 1.21+ xem vật phẩm này là Tool/Generic thay vì Armor. Khi có Unbreaking 3, Tool chỉ có 25% tỷ lệ nhận sát thương (1/(level+1)), trong khi Giáp chuẩn có tới 70% tỷ lệ nhận sát thương (0.6 + 0.4/(level+1)) -> Mũ Nexo bị lâu hỏng gấp 2.8 lần! Đồng thời khi không có Enchant, hurtArmor của vanilla/Paper bỏ qua vật phẩm non-armor ở các sát thương như Thorns phản đòn hay Falling Anvil.

        Giải pháp khắc phục:

            1. Chặn hoàn toàn PlayerItemDamageEvent (EventPriority.LOWEST) đối với Custom Hat để triệt tiêu việc Paper/Minecraft tính sai theo công thức Tool.

            2. Lắng nghe EntityDamageEvent (EventPriority.MONITOR, ignoreCancelled = true) và gọi applyCombatDamageToHat:

                - Xác thực nguồn sát thương giáp hợp lệ (ENTITY_ATTACK, PROJECTILE, EXPLOSION, FALLING_BLOCK, LIGHTNING, THORNS, FIRE, LAVA, CAMPFIRE, HOT_FLOOR, CONTACT...).

                - Kiểm tra miễn nhiễm lửa (Fire Immunity) cho mũ Netherite khi bị đốt bởi lửa/dung nham.

                - Tính toán sát thương thực tế sau khi bị Shield chặn (getEffectiveIncomingDamage).

                - Áp dụng công thức sát thương giáp chuẩn Minecraft: rawArmorDamage = (int) Math.max(1.0, effectiveDamage / 4.0).

                - Áp dụng công thức Unbreaking chuẩn Giáp: chance = 0.6 + (0.4 / (unbreakingLevel + 1.0)).

                - Tính thêm sát thương hao mòn phản đòn gai (Thorns Degradation: tỷ lệ thornsLevel * 15%, trừ thêm 2 điểm độ bền).

                - Cập nhật số điểm sát thương mới vào PDC HELMET_DAMAGE_KEY và ItemMeta Damageable.

                - Khi độ bền cạn kiệt (newDamage >= maxDurability): Tự động phát âm thanh Sound.ENTITY_ITEM_BREAK, hiệu ứng hạt Particle.ITEM và xóa sạch mũ trên đầu (setHelmet(null)).

            3. Lắng nghe PlayerItemMendEvent (EventPriority.MONITOR, ignoreCancelled = true): Tự động trừ điểm hư hại trong PDC khi người chơi nhặt XP để hồi máu cho mũ theo Mending.

            4. Khi hoàn trả mũ gốc (restoreOriginalHelmet): Lấy số damage tích lũy từ PDC gán vào Damageable của mũ Diamond/Netherite gốc. Nếu mũ đã hỏng (currentDamage >= maxDurability), hoàn trả Material.AIR và dọn sạch khỏi túi đồ/rơi đất.

    Chống Flicker & Tương thích Kit:

        Khi người chơi mở túi đồ cá nhân (InventoryType.CRAFTING), isExternalGui() trả về false để không tháo mũ ra gắn lại, triệt tiêu hoàn toàn lỗi nhấp nháy model trên đầu.

        Khi mở GUI ngoài (Rương, EnderChest, Shulker, GUI Kit...) hoặc gõ lệnh /kit, /regear, /spk ở EventPriority.LOWEST, tự động hoàn trả lại mũ Diamond/Netherite gốc trước khi plugin khác đọc kho đồ.

4.5. Hệ Thống KillEffect Studio Hub & 3D ModelEngine (KillEffectHubGui.java & KillEffectStudioGui.java)

    Menu Lựa Chọn KillEffect (/ke - KillEffectHubGui.java):
        - Khi gõ /ke (hoặc click icon KillEffect Studio ở ô 13 của /decor), mở giao diện 27 ô KillEffectHubGui chỉ hiển thị đúng 2 nút chức năng ở hàng giữa đối xứng:
            - Ô 12: Classic KillEffect (FIREWORK_ROCKET) → Đóng GUI và chạy lệnh /killeffects của plugin hạt cổ điển.
            - Ô 14: Modern KillEffect (3D) (TOTEM_OF_UNDYING) → Mở KillEffectStudioGui để chọn hiệu ứng 3D ModelEngine.
            - Ô 18: Nút Back quay về DecorHubGui.
            - Ô 26: Nút Admin 3D Manager (nếu người chơi có quyền Admin).
        - Không hiển thị nút trạng thái đã chọn ở giữa (loại bỏ ô 13), giữ giao diện tinh gọn, trực quan và tập trung cho 2 lựa chọn.
        - Hỗ trợ các lệnh tắt: /ke classic (chạy ngay menu hạt cổ điển), /ke 3d hoặc /ke modern (mở thẳng menu 3D).

    Hook trực tiếp ModelEngine 4 API qua Reflection (KillEffectManager.java):

        Không phụ thuộc MythicMobs. Dùng ClassLoader của plugin ModelEngine gọi ModelEngineAPI.getBlueprint(modelId), createModeledEntity(stand), createActiveModel(modelId).

        Spawn ArmorStand tàng hình (Visible: false, Invulnerable: true, Gravity: false, Marker: true / Non-collidable, Persistent: false) với Pitch = 0 và hướng mặt về phía kẻ giết (killer) trong PvP.

        Chống Lệch & Tách Rời Xương Đầu (Head Bone Fix): Quét xương kiểm tra BoneBehaviorTypes.HEAD và gọi setLocal(true) để đầu không bị xoay độc lập theo LookController toàn cục gây lệch/vẹo đầu khi model di chuyển/animate.

        Kế thừa Skin nạn nhân (PlayerLimb & Item Skull): Quét toàn bộ xương (getBones()) của ActiveModel:
        - Với BoneBehaviorTypes.PLAYER_LIMB: Hỗ trợ gọi setTexture/setSkin đa kiểu dữ liệu (Player, UUID, String, OfflinePlayer) với setAccessible(true).
        - Với BoneBehaviorTypes.ITEM (xương chứa head/skull): Gán vật phẩm PLAYER_HEAD có SkullMeta mang skin nạn nhân.

        Kích hoạt Animation chuẩn Blueprint & Chống Giật/Flick Cuối Hiệu Ứng (Anti-Flicker Lifecycle Fix):
        - Tự động dò animation trong ModelBlueprint (ưu tiên play, activate, trigger, spawn, death, appear, idle, v.v.).
        - Thiết lập LoopMode.HOLD trực tiếp trên cả BlueprintAnimation và IAnimationProperty để khi hoạt ảnh kết thúc, model đứng yên ở frame cuối cùng (nơi hiệu ứng đã phân rã/tan biến vào không khí) thay vì tự động snap/flick ngược về tư thế đứng ban đầu (bind pose/rest pose).
        - Gán lerpOut = 0.0 trong playAnimation để triệt tiêu hoàn toàn độ trễ pha trộn trả về rest pose.
        - Tính toán chính xác thời lượng (getLength()) theo Math.ceil(sec * 20.0f) mà không cộng thêm buffer thừa gây lưu ảnh.
        - Quy trình dọn dẹp thực thể chuẩn (ActiveEffect.destroy()): Gọi tuần tự activeModel.destroy() -> modeledEntity.removeModel(modelId) -> modeledEntity.destroy() -> ModelEngineAPI.removeModeledEntity(...) trước khi xóa ArmorStand, đảm bảo ModelEngine gửi packet despawn ngay lập tức mà không để lại ghost bones hay delay 1 tick.
        - Quản lý bộ nhớ preview cá nhân (activePreviews): Hủy và dọn dẹp preview cũ ngay lập tức nếu người chơi click liên tục hoặc rời mạng, chống chồng lấn nhiều model cùng lúc.

        Tự động phát âm thanh gói ec_killeffects_sfx4:kill_fx.<suffix> trên SoundCategory.MASTER và hiệu ứng hạt Particles đặc trưng theo từng model (frost, abstracted, graffiti, plasma, magnetic, ink, pure).

        Tự động dọn sạch toàn bộ thực thể đang active trong onDisable().

4.6. Hệ Thống Item Rename & Chống Đè Màu Rarity (RenameManager.java & RenameStudioGui.java)

    Khắc phục triệt để lỗi mất màu &c và lỗi đè màu xanh Aqua Rarity trên 1.21+:

        Trong AntiArmorTrim.parseItemName(String input):

            Nếu chuỗi không chứa thẻ <...>, dùng trực tiếp LegacyComponentSerializer (character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build()) sau khi chuẩn hóa § thành &, và gọi .decoration(TextDecoration.ITALIC, false). Cách này gán màu trực tiếp lên Root Component thay vì tạo Component rỗng color=null như MiniMessage, ngăn chặn hoàn toàn việc client Minecraft 1.21+ đè màu xanh Aqua (#55FFFF) của đồ Enchant lên tên vật phẩm.

            Nếu chuỗi có chứa <...>, chuyển đổi mã & sang MiniMessage rồi parse qua miniMessage.deserialize(..., createGlyphTagResolver()).decoration(TextDecoration.ITALIC, false).

        Trong AsyncChatEvent (onChatRename), ưu tiên lấy chuỗi gốc từ event.signedMessage().message() trước khi fallback về PlainTextComponentSerializer để không bị các plugin chat làm mất ký tự &.

    Cơ chế Quét Xóa Vật Phẩm Đổi Tên Trái Phép:

        Vật phẩm đổi tên qua /rename được gắn thẻ PDC antitrim:custom_renamed = true.

        Nếu người chơi không có quyền armortrim.rename (hoặc OP/Bypass) nhặt, click trong kho đồ, hoặc cầm vật phẩm có thẻ này, TrimListener sẽ xóa lập tức vật phẩm đó, phát âm thanh ENTITY_ITEM_BREAK và gửi cảnh báo rename-unauthorized-pickup.

4.7. Hệ Thống Combat Tracker, Tap-Shot TNT Minecart & Death Message (CombatTracker.java)

    Theo dõi toàn diện vũ khí gây sát thương:

        Ghi nhận vũ khí bắn tên trong EntityShootBowEvent (projectileWeaponMap).

        Sửa lỗi bắn cung vào TNT Minecart phải Full Charge mới nổ và hiện tên:

            Lắng nghe ProjectileHitEvent (EventPriority.HIGHEST) và VehicleDamageEvent: Ngay khi mũi tên chạm vào TNT_MINECART (ExplosiveMinecart), gọi markExplosiveTrigger(hitEntity, proj) để lưu người bắn + cây cung đã dùng, đồng thời gọi em.explode() ở tick kế tiếp. Nhờ đó, dù chỉ nhấp nhẹ cung 10% lực (tap-shot), cỗ xe TNT Minecart vẫn phát nổ ngay lập tức và lưu chính xác cây cung đã đổi tên.

        Hỗ trợ đầy đủ cả END_CRYSTAL, TNT, RESPAWN_ANCHOR và BED (ghi nhận qua PlayerInteractEvent).

    Định dạng Death Message (EventPriority.HIGHEST):

        Chữ " using " và cặp dấu ngoặc vuông [ ] đều được định dạng màu trắng chuẩn NamedTextColor.WHITE để tiệp màu 100% với thông báo chết mặc định của Minecraft:

            Khi giết người khác (PvP): <Victim> was blown up by <Killer> using [<Tên Vũ Khí>] (hoặc nối vào sau câu thông báo chết gốc).

            Khi tự sát (Self-kill bằng vụ nổ của chính mình): Đổi từ <Player> blew up thành <Player> was blown up by [<Tên Vũ Khí>].

            Đính kèm .hoverEvent(weapon.asHoverEvent()) lên [<Tên Vũ Khí>] để mọi người chơi rê chuột vào xem được đầy đủ tên màu, Enchantments và Lore của vũ khí.

5. CÁC QUY TẮC KỸ THUẬT BẤT KHẢ XÂM PHẠM (CRITICAL ENGINEERING RULES)

    Quy tắc ký tự Đô-la trong chuỗi Reflection:

        Tuyệt đối KHÔNG viết ký tự $ trực tiếp trong chuỗi Reflection của TooltipDisplay$Builder (để tránh lỗi cắt chuỗi của công cụ AI), BẮT BUỘC viết là:
        "io.papermc.paper.datacomponent.item.TooltipDisplay\u0024Builder"

    Quy tắc ClassLoader cho Nexo & ModelEngine:

        Khi gọi Reflection tới com.nexomc.nexo.* hoặc com.ticxo.modelengine.*, BẮT BUỘC truyền plugin.getClass().getClassLoader() vào Class.forName(name, true, loader).

    Quy tắc ưu tiên kiểm tra player.isOp() đầu tiên:

        Mọi hàm kiểm tra quyền (canUseBypass, canUseHat, canUseSpecificHat, canUseKillEffect, canUseSpecificKillEffect, canUseKillSound, canUseSpecificKillSound, canUseRename, hasAdminPerm) BẮT BUỘC có player.isOp() || ... ở đầu tiên.

    Quy tắc tự động đồng bộ config.yml (syncConfigWithDefaults):

        Dùng khóa config-version: 6 trong config.yml. Khi plugin khởi động hoặc chạy /at reload, nếu config-version < 6, plugin tự động nạp nhánh prefixes, gui và messages mới nhất từ resource trong .jar ghi xuống plugins/AntiArmorTrim/config.yml trên đĩa.

    Quy tắc khóa thời gian chống StackOverflowError:

        Khóa ADVANCE_TIME, ADVANCE_WEATHER, DO_DAYLIGHT_CYCLE, DO_WEATHER_CYCLE bằng Reflection và dùng task định kỳ mỗi 60 ticks để giữ world.setTime(6000L). Tuyệt đối KHÔNG lắng nghe TimeSkipEvent rồi gọi setTime() bên trong nó.

6. CẤU HÌNH TỐI ƯU PACKSQUASH + NEXO + MODELENGINE TRÊN SERVER LINUX

Server sử dụng PackSquash v0.4.1 tích hợp trong Nexo để nén toàn bộ Resource Pack (Nexo + ModelEngine) xuống chỉ còn ~0.539 MiB (550 KB), giúp máy yếu (4GB RAM, Intel HD Graphics) tải pack trong 0.5 giây không bị crash Client shutdown watchdog:

    Wrapper Script tự động vá lỗi Shader #include của ModelEngine R4.2.0:

        Tại thư mục /home/container/plugins/Nexo/pack/packsquash/:

            File binary gốc của PackSquash (x86_64-unknown-linux-gnu) được đổi tên thành packsquash.bin (chmod 755).

            File packsquash (chmod 755, chuẩn dòng LF) là một shell script 5 dòng tự động chuyển #include thành #moj_import trong .cache trước khi gọi packsquash.bin:
            code Sh

            #!/bin/sh
            for f in /home/container/plugins/Nexo/pack/packsquash/.cache/*/assets/minecraft/shaders/core/entity.*; do
              [ -f "$f" ] && sed -i 's/#include/#moj_import/g' "$f"
            done
            exec /home/container/plugins/Nexo/pack/packsquash/packsquash.bin "$@"

    Trong /home/container/plugins/Nexo/settings.yml:

        Pack.obfuscation.type: NONE và cache: false.

        Pack.dispatch.send_pre_join: false, send_on_join: true, delay: 40 (trì hoãn 2 giây sau khi vào server mới nạp pack).

    Trong /home/container/plugins/Nexo/pack/packsquash/packsquash.toml:

        Quy tắc cụ thể đặt trước quy tắc chung, bật transcode_ogg = true (Mono channels = 1, 32000Hz cho âm thanh KillEffect), shader_source_transformation_strategy = 'keep_as_is', và ['modelengine_1_21_6/**'] force_include = true.

---

## 7. OPERATIONAL RULES (TỰ ĐỘNG ĐỒNG BỘ GITHUB SAU MỖI LẦN UPDATE)

Mọi AI Agent hoặc lập trình viên làm việc trên repository này BẮT BUỘC phải tuân thủ quy trình đồng bộ lên GitHub (`https://github.com/UnoVN/VCTrimRemover`) sau mỗi lần thay đổi code hoặc cấu hình:

1. **Kiểm tra Build/Compile:**
   - Sau mọi chỉnh sửa mã nguồn (`.java`) hoặc cấu hình (`config.yml`, `plugin.yml`, `pom.xml`), phải biên dịch và đóng gói kiểm tra qua Maven (`mvn clean package --batch-mode`) để đảm bảo build không có lỗi.
2. **Cập nhật Tài liệu Kiến trúc (`GEMINI.md`):**
   - Nếu cấu trúc file, chữ ký hàm công khai, bảng SQLite, `config-version` hoặc luồng xử lý chính thay đổi, phải cập nhật `GEMINI.md` cho khớp với code thực tế.
3. **Kiểm tra trạng thái Git & Lọc file sạch sẽ:**
   - Chạy `git status` để rà soát toàn bộ thay đổi, chắc chắn `.gitignore` đang chặn đúng các thư mục/file build (`target/`, `build/`, `*.jar`), IDE (`.vscode/`, `.idea/`), OS artifacts và file dữ liệu runtime/nhạy cảm (`*.db`, `.env`). Không xóa file build local, chỉ giữ ignore khỏi Git.
4. **Commit & Push tự động không tương tác:**
   - Thực hiện `git add -A` và `git commit` với commit message tiếng Anh/tiếng Việt rõ ràng mô tả đúng phạm vi thay đổi.
   - Chạy `GIT_TERMINAL_PROMPT=0 git push origin <branch>` (với `<branch>` thường là `main`).
   - **Quy tắc an toàn:** Tuyệt đối KHÔNG dùng `--force` / `--force-with-lease`. Luôn truyền `GIT_TERMINAL_PROMPT=0` để tránh treo lệnh khi xác thực (máy sử dụng `credential.helper=libsecret`). Nếu xảy ra lỗi xác thực, quyền truy cập hoặc conflict từ remote, phải dừng ngay và báo rõ nguyên nhân cho người dùng.
5. **Hook bảo vệ Lớp 2 (`.git/hooks/post-commit`):**
   - Repo có cài đặt hook `.git/hooks/post-commit` để tự động đẩy commit lên `origin` ngay sau mỗi lệnh `git commit` thủ công. Lưu ý hook này chỉ cảnh báo ra `stderr` nếu mất mạng/lỗi auth chứ không làm hỏng commit local.

