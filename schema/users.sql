CREATE TABLE `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(85) CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `password_hash` varchar(64) DEFAULT NULL,
  `prikey_enc` text NOT NULL,
  `hash_params` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `key_hash_params` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `key_enc_params` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `pub_key` text CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `identity_token` varchar(64) NOT NULL,
  `metadata` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NOT NULL,
  `status` smallint(6) NOT NULL,
  `created_at` datetime NOT NULL,
  `registration_ip` int(10) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=10108 DEFAULT CHARSET=latin1