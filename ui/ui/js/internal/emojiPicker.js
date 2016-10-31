slychat.emojiPicker = function () {
    var filters = [
        {
            id: "smileys",
            icon: "smile",
            title: "Smileys & People",
            emoji: "grinning grimacing grin joy smiley smile sweat_smile laughing innocent wink blush slight_smile " +
            "upside_down relaxed yum relieved heart_eyes kissing_heart kissing kissing_smiling_eyes " +
            "kissing_closed_eyes stuck_out_tongue_winking_eye stuck_out_tongue_closed_eyes stuck_out_tongue " +
            "money_mouth nerd sunglasses hugging smirk no_mouth neutral_face expressionless unamused rolling_eyes " +
            "thinking flushed disappointed worried angry rage pensive confused slight_frown frowning2 persevere " +
            "confounded tired_face weary triumph open_mouth scream fearful cold_sweat hushed frowning anguished " +
            "cry disappointed_relieved sleepy sweat sob dizzy_face astonished zipper_mouth mask thermometer_face " +
            "head_bandage sleeping zzz poop smiling_imp imp japanese_ogre japanese_goblin skull ghost alien robot " +
            "smiley_cat smile_cat joy_cat heart_eyes_cat smirk_cat kissing_cat scream_cat crying_cat_face " +
            "pouting_cat raised_hands clap wave thumbsup thumbsdown punch fist v ok_hand raised_hand open_hands " +
            "muscle pray point_up point_up_2 point_down point_left point_right middle_finger hand_splayed metal " +
            "vulcan writing_hand nail_care lips tongue ear nose eye eyes bust_in_silhouette busts_in_silhouette " +
            "speaking_head baby boy girl man woman person_with_blond_hair older_man older_woman man_with_gua_pi_mao " +
            "man_with_turban cop construction_worker guardsman spy santa angel princess bride_with_veil walking " +
            "runner dancer dancers couple two_men_holding_hands two_women_holding_hands bow information_desk_person " +
            "no_good ok_woman raising_hand person_with_pouting_face person_frowning haircut massage couple_with_heart " +
            "couple_ww couple_mm couplekiss kiss_ww kiss_mm family family_mwg family_mwgb family_mwbb family_mwgg " +
            "family_wwb family_wwg family_wwgb family_wwbb family_wwgg family_mmb family_mmg family_mmgb family_mmbb " +
            "family_mmgg womans_clothes shirt jeans necktie dress bikini kimono lipstick kiss footprints high_heel " +
            "sandal boot mans_shoe athletic_shoe womans_hat tophat helmet_with_cross mortar_board crown school_satchel " +
            "pouch purse handbag briefcase eyeglasses dark_sunglasses ring closed_umbrella"
        },

        {
            id: "animal",
            icon: "dog",
            title: "Animals & Nature",
            emoji: "dog cat mouse hamster rabbit bear panda_face koala tiger lion_face cow pig pig_nose frog " +
            "octopus monkey_face see_no_evil hear_no_evil speak_no_evil monkey chicken penguin bird baby_chick " +
            "hatching_chick hatched_chick wolf boar horse unicorn bee bug snail beetle ant spider scorpion crab " +
            "snake turtle tropical_fish fish blowfish dolphin whale whale2 crocodile leopard tiger2 water_buffalo " +
            "ox cow2 dromedary_camel camel elephant goat ram sheep racehorse pig2 rat mouse2 rooster turkey dove " +
            "dog2 poodle cat2 rabbit2 chipmunk feet dragon dragon_face cactus christmas_tree evergreen_tree " +
            "deciduous_tree palm_tree seedling herb shamrock four_leaf_clover bamboo tanabata_tree leaves " +
            "fallen_leaf maple_leaf ear_of_rice hibiscus sunflower rose tulip blossom cherry_blossom bouquet " +
            "mushroom chestnut jack_o_lantern shell spider_web earth_americas earth_africa earth_asia full_moon " +
            "waning_gibbous_moon last_quarter_moon waning_crescent_moon new_moon waxing_crescent_moon " +
            "first_quarter_moon waxing_gibbous_moon new_moon_with_face full_moon_with_face first_quarter_moon_with_face " +
            "last_quarter_moon_with_face sun_with_face crescent_moon star star2 dizzy sparkles comet sunny " +
            "white_sun_small_cloud partly_sunny white_sun_cloud white_sun_rain_cloud cloud cloud_rain " +
            "thunder_cloud_rain cloud_lightning zap fire boom snowflake cloud_snow snowman2 snowman wind_blowing_face " +
            "dash cloud_tornado fog umbrella2 umbrella droplet sweat_drops ocean"
        },

        {
            id: "food",
            icon: "pizza",
            title: "Food & Drink",
            emoji: "green_apple apple pear tangerine lemon banana watermelon grapes strawberry melon cherries peach " +
            "pineapple tomato eggplant hot_pepper corn sweet_potato honey_pot bread cheese poultry_leg meat_on_bone " +
            "fried_shrimp egg hamburger fries hotdog pizza spaghetti taco burrito ramen stew fish_cake sushi bento " +
            "curry rice_ball rice rice_cracker oden dango shaved_ice ice_cream icecream cake birthday custard candy " +
            "lollipop chocolate_bar popcorn doughnut cookie beer beers wine_glass cocktail tropical_drink champagne " +
            "sake tea coffee baby_bottle fork_and_knife fork_knife_plate"
        },

        {
            id: "sport",
            icon: "basketball",
            title: "Activity",
            emoji: "soccer basketball football baseball tennis volleyball rugby_football 8ball golf golfer ping_pong " +
            "badminton hockey field_hockey cricket ski skier snowboarder ice_skate bow_and_arrow fishing_pole_and_fish " +
            "rowboat swimmer surfer bath basketball_player lifter bicyclist mountain_bicyclist horse_racing levitate " +
            "trophy running_shirt_with_sash medal military_medal reminder_ribbon rosette ticket tickets performing_arts " +
            "art circus_tent microphone headphones musical_score musical_keyboard saxophone trumpet guitar violin " +
            "clapper video_game space_invader dart game_die slot_machine bowling"
        },

        {
            id: "travel",
            icon: "police_car",
            title: "Travel & Places",
            emoji: "red_car taxi blue_car bus trolleybus race_car police_car ambulance fire_engine minibus truck " +
            "articulated_lorry tractor motorcycle bike rotating_light oncoming_police_car oncoming_bus " +
            "oncoming_automobile oncoming_taxi aerial_tramway mountain_cableway suspension_railway railway_car " +
            "train monorail bullettrain_side bullettrain_front light_rail mountain_railway steam_locomotive train2 " +
            "metro tram station helicopter airplane_small airplane airplane_departure airplane_arriving sailboat " +
            "motorboat speedboat ferry cruise_ship rocket satellite_orbital seat anchor construction fuelpump busstop " +
            "vertical_traffic_light traffic_light checkered_flag ship ferris_wheel roller_coaster carousel_horse " +
            "construction_site foggy tokyo_tower factory fountain rice_scene mountain mountain_snow mount_fuji volcano " +
            "japan camping tent park motorway railway_track sunrise sunrise_over_mountains desert beach island " +
            "city_sunset city_dusk cityscape night_with_stars bridge_at_night milky_way stars sparkler fireworks " +
            "rainbow homes european_castle japanese_castle stadium statue_of_liberty house house_with_garden " +
            "house_abandoned office department_store post_office european_post_office hospital bank hotel " +
            "convenience_store school love_hotel wedding classical_building church mosque synagogue kaaba shinto_shrine"
        }
    ];

    function createNavbarButton(icon, id, first) {
        var img = emojione.toImage(':' + icon + ':');
        var c = '';
        if (first)
            c = 'active';

        return '<a href="#' + id + '" class="button tab-link ' + c + '">' + img + '</a>';
    }

    function createTab(icons, id, first) {
        var c = '';
        if (first)
            c = 'active';

        var links = '';
        icons.split(" ").forEach(function (icon) {
            links += '<a class="emoticone-link" data-emoji=":' + icon + ':" href="#">' + emojione.toImage(":" + icon + ":") + '</a>';
        });
        return '<div id="' + id + '" class="page-content tab ' + c + '">' +
            '<div class="content-block" style="margin-top: 5px; padding: 0;">' +
            links +
            '</div>' +
            '</div>';
    }

    function createDesktopEmojiPicker(navbar, tabs) {
        return '' +
            '<div class="popover popover-emoji popover-on-top" style="height: 200px;">' +
            '<div class="popover-inner">' +
            '<div class="emoji-picker-header toolbar tabbar" style="height: 40px; width: 100%;">' +
            '<div class="toolbar-inner">' +
            navbar +
            '</div>' +
            '</div>' +
            '<div class="tabs" style="height: 160px; overflow-x: auto;">' +
            tabs +
            '</div>' +
            '</div>' +
            '</div>';
    }

    function createMobileEmojiPicker(navbar, tabs) {
        return "" +
            "<div class='mobile-emoji-picker' style='height: 295px; width: 100%; position: absolute; bottom: 0; display: none;'>" +
                "<div class='toolbar tabbar' style='height: 40px; position: absolute; top: 0;'>" +
                    "<div class='toolbar-inner'>" +
                        navbar +
                    "</div>" +
                "</div>" +
                "<div class='tabs' style='height: 295px; overflow-x: auto;'>" +
                    tabs +
                "</div>" +
            "</div>";
    }

    var navbar = "";
    var tabs = "";
    var first = true;
    filters.forEach(function (filter) {
        navbar += createNavbarButton(filter.icon, filter.id, first);
        tabs += createTab(filter.emoji, filter.id, first);
        first = false;
    });

    var picker;
    if (isDesktop) {
        picker = createDesktopEmojiPicker(navbar, tabs);
        $("body").append(picker);
    }
    else {
        picker = createMobileEmojiPicker(navbar, tabs);
        $(".page").after(picker);
    }
};

function toggleMobileEmoji () {
    if ($(".mobile-emoji-picker").length <= 0)
        return;

    if ($(".mobile-emoji-picker-opened").length > 0) {
        closeMobileEmoji();
    }
    else {
        openMobileEmoji();
    }
}

function openMobileEmoji () {
    if (isSoftKeyboardVisible) {
        slychat.keyboardWasVisible = true;
        $("#emojiPickerBtn").focus();
        $(".page").addClass("mobile-emoji-picker-opened");
        setTimeout(function () {
            $(".mobile-emoji-picker").show();
        }, 200);
    }
    else {
        slychat.keyboardWasVisible = false;
        $(".page").addClass("mobile-emoji-picker-opened");
        $(".mobile-emoji-picker").show();
    }
}

function closeMobileEmoji () {
    $(".mobile-emoji-picker").hide();
    $(".page").removeClass("mobile-emoji-picker-opened");
}

