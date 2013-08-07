/*
 * Copyright (C) 2013 MorihiroSoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <android/log.h>
#define  LOG_TAG "CNVAVC"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#if 0
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#else
#define  LOGI(...)
#endif

#define BUF_SIZE (1024)
#define INIT_FRAME_SIZE (1024)
#define INIT_SPS_SIZE (256)
#define INIT_PPS_SIZE (256)

#pragma pack(1)

//
int32_t cnvavc(const char* src_path, const char* dst_path,
		int32_t video_w, int32_t video_h, int32_t fps)
{
	const uint8_t START_PAT[] = {0x00,0x00,0x00,0x01};
	const uint8_t NAL_TYPE_MASK = 0x1F;
	const uint8_t NAL_TYPE_SPS  = 0x07; // 0x67,0x27
	const uint8_t NAL_TYPE_PPS  = 0x08; // 0x68,0x28
	const uint8_t NAL_TYPE_SYNC = 0x05; // 0x65,0x25

	//
	struct _frame_info {
		int32_t  frame_idx;
		uint32_t addr;
		uint32_t size;
		uint8_t  nal_type;
	};

	int32_t             rcode = 0;
	FILE*               src_fp = NULL;
	FILE*               dst_fp = NULL;
	uint8_t*            buf = NULL;
	uint8_t*            bufP;
	uint32_t            frame_info_cnt = 0;
	uint32_t            frame_info_alc = 0;
	struct _frame_info* frame_infos = NULL;
	uint32_t            full_frame_cnt = 0; // except SPS/PPS
	uint32_t            sync_frame_cnt = 0;
	uint16_t            sps_len = 0;
	uint32_t            sps_alc = 0;
	uint8_t*            sps_dat = NULL;
	uint16_t            pps_len = 0;
	uint32_t            pps_alc = 0;
	uint8_t*            pps_dat = NULL;
	int32_t             i, j;
	uint32_t            num;

	do {
		//-------------------------------------------------------------
		// Phase 0 - Open/Create file and Others...
		//-------------------------------------------------------------
		frame_info_alc = INIT_FRAME_SIZE;
		sps_alc        = INIT_SPS_SIZE;
		pps_alc        = INIT_PPS_SIZE;

		// Allocate
		buf         = (uint8_t           *)malloc(sizeof(uint8_t) * BUF_SIZE);
		frame_infos = (struct _frame_info*)malloc(sizeof(struct _frame_info) * frame_info_alc);
		sps_dat     = (uint8_t           *)malloc(sizeof(uint8_t) * sps_alc);
		pps_dat     = (uint8_t           *)malloc(sizeof(uint8_t) * pps_alc);
		if (buf == NULL || frame_infos == NULL || sps_dat == NULL || pps_dat == NULL) {
			LOGE("%s(L=%d): malloc error", __func__, __LINE__);
			break;
		}

		// Open
		src_fp = fopen(src_path, "rb");
		if (src_fp == NULL) {
			LOGE("%s(L=%d): fopen error(src_path=%s)", __func__, __LINE__, src_path);
			break;
		}

		dst_fp = fopen(dst_path, "wb");
		if (dst_fp == NULL) {
			LOGE("%s(L=%d): fopen error(dst_path=%s)", __func__, __LINE__, dst_path);
			break;
		}

		//-------------------------------------------------------------
		// Phase 1 - Scan
		//-------------------------------------------------------------
		int32_t start_idx = 0;
		int32_t sps_flg = 0;
		int32_t pps_flg = 0;

		// Check frame address.
		uint32_t src_addr = 0;
		while(!feof(src_fp)) {
			num = fread(buf, 1, BUF_SIZE, src_fp);
			for (i=0,bufP=buf; i<num; i++,bufP++) {
				// Store SPS/PSP
				if (sps_flg != 0) {
					if (sps_len == sps_alc) {
						sps_alc *= 2;
						sps_dat = (uint8_t*)realloc(sps_dat, sizeof(uint8_t) * sps_alc);
					}
					sps_dat[sps_len++] = *bufP;
				} else if (pps_flg != 0) {
					if (pps_len == pps_alc) {
						pps_alc *= 2;
						pps_dat = (uint8_t*)realloc(pps_dat, sizeof(uint8_t) * pps_alc);
					}
					pps_dat[pps_len++] = *bufP;
				}

				//
				if (start_idx == sizeof(START_PAT)) {
					uint8_t nal_type = (*bufP & NAL_TYPE_MASK);

					start_idx = 0;
					if (frame_info_cnt == frame_info_alc) {
						frame_info_alc *= 2;
						frame_infos = (struct _frame_info*)realloc(frame_infos, sizeof(struct _frame_info) * frame_info_alc);
					}

					// Terminate SPS/PPS
					if (sps_flg != 0) {
						sps_len -= sizeof(START_PAT) + 1;
						sps_flg = 0;
					} else if (pps_flg != 0) {
						pps_len -= sizeof(START_PAT) + 1;
						pps_flg = 0;
					}

					// Start SPS/PPS or SYNC
					if (nal_type == NAL_TYPE_SPS) {
						frame_infos[frame_info_cnt].frame_idx = -1;
						if (sps_len > 0) {
							// Failure
							LOGE("%s(L=%d): Multiple SPS, src_addr=0x%08X, i=0x%08X", __func__, __LINE__, src_addr, i);
							rcode = -1;
							break;
						}
						sps_dat[sps_len++] = *bufP;
						sps_flg = 1;
					} else if (nal_type == NAL_TYPE_PPS) {
						frame_infos[frame_info_cnt].frame_idx = -2;
						if (pps_len > 0) {
							// Failure
							LOGE("%s(L=%d): Multiple PPS, src_addr=0x%08X, i=0x%08X", __func__, __LINE__, src_addr, i);
							rcode = -2;
							break;
						}
						pps_dat[pps_len++] = *bufP;
						pps_flg = 1;
					} else {
						frame_infos[frame_info_cnt].frame_idx = full_frame_cnt++;
						if (nal_type == NAL_TYPE_SYNC) {
							sync_frame_cnt++;
						}
					}
					frame_infos[frame_info_cnt].addr = src_addr + i - sizeof(START_PAT);
					frame_infos[frame_info_cnt].nal_type = nal_type;
					frame_info_cnt++;
				} else if (*bufP == START_PAT[start_idx]) {
					start_idx++;
				} else if (start_idx > 0 && *bufP != START_PAT[start_idx-1]) {
					start_idx = 0;
				}
			}
			src_addr += num;
		}
		// Failure ?
		if (rcode != 0) {
			LOGE("%s(L=%d): Failure", __func__, __LINE__);
			break;
		}
		if (sps_len == 0 || pps_len == 0) {
			LOGE("%s(L=%d): Failure, sps_len=%d, pps_len=%d", __func__, __LINE__, sps_len, pps_len);
			rcode = -3;
			break;
		}

		// Convert address to size.
		for (i=0; i<frame_info_cnt-1; i++) {
			frame_infos[i].size = frame_infos[i+1].addr - frame_infos[i].addr - sizeof(START_PAT);
		}
		frame_infos[frame_info_cnt-1].size = src_addr - frame_infos[frame_info_cnt-1].addr - sizeof(START_PAT);

		//
		rewind(src_fp);

		//-------------------------------------------------------------
		// Phase 2 - Convert
		//-------------------------------------------------------------
		time_t  now = time(NULL);
		int32_t duration = 1000 * full_frame_cnt / fps;

		uint32_t dst_addr = 0;

		// Dst: FTYP
		{
			struct _ftyp {
				uint32_t size;
				uint8_t  kind[4];
				uint8_t  major_brand[4];
				int32_t  major_brand_version;
				uint8_t  compatible_brands_1[4];
				uint8_t  compatible_brands_2[4];
				uint8_t  compatible_brands_3[4];
				uint8_t  compatible_brands_4[4];
			} box_ftyp = {
					htonl(sizeof(struct _ftyp)),
					{'f','t','y','p'},
					{'i','s','o','m'},
					htonl(0x00000200),
					{'i','s','o','m'},
					{'i','s','o','2'},
					{'a','v','c','1'},
					{'m','p','4','1'},
			};
			dst_addr += fwrite(&box_ftyp, sizeof(box_ftyp), 1, dst_fp) * sizeof(box_ftyp);
		}

		// Dst: FREE
		{
			struct _free {
				uint32_t size;
				uint8_t  kind[4];
			} box_free = {
					htonl(sizeof(struct _free)),
					{'f','r','e','e'},
			};
			dst_addr += fwrite(&box_free, sizeof(box_free), 1, dst_fp) * sizeof(box_free);
		}

		// Dst: MOOV
		{
			struct _stsd_avc1_avcC_part2 {
				uint8_t  number_of_pps;
				uint16_t pps_length;
			} box_stsd_avc1_avcC_part2 = {
					1,
					htons(pps_len),
			};
			struct _stsd_avc1_avcC_part1 {
				uint32_t size;
				uint8_t  kind[4];
				uint8_t  version; // = 1
				uint8_t  h264_profile;
				uint8_t  h264_compatible_profile;
				uint8_t  h264_level;
				uint8_t  nal_length_type; // = (0xFC|type) = 0xFF
				uint8_t  number_of_sps; // = (0xE0|num) = 0xE1
				uint16_t sps_length;
				//uint8_t  sps_nal_unit[sps_length];
				//uint8_t  number_of_pps;
				//uint16_t pps_length;
				//uint8_t  pps_nal_unit[pps_length];
			} box_stsd_avc1_avcC_part1 = {
					htonl(sizeof(struct _stsd_avc1_avcC_part1)+sps_len+sizeof(struct _stsd_avc1_avcC_part2)+pps_len),
					{'a','v','c','C'},
					1,
					sps_dat[1],
					sps_dat[2],
					sps_dat[3],
					0xFF,
					0xE1,
					htons(sps_len),
			};
			uint32_t stsd_avc1_avcC_size =
					sizeof(struct _stsd_avc1_avcC_part1) +
					sps_len +
					sizeof(struct _stsd_avc1_avcC_part2) +
					pps_len;

			struct _stsd {
				uint32_t size;
				uint8_t  kind[4];
				uint32_t version1_flags3;
				int32_t  entry_count; // 1 = default
				struct _stsd_avc1 {
					uint32_t size;
					uint8_t  kind[4];
					uint8_t  reserve[6];
					int16_t  reference_index;
					int16_t  predefined;
					int16_t  reserve2[1];
					int32_t  predefined2[3];
					int16_t  frame_width;
					int16_t  frame_height;
					uint32_t horiz_resolution; // 0x00480000 = 72dpi
					uint32_t vert_resolution;  // 0x00480000 = 72dpi
					uint32_t reserve3[1];
					int16_t  video_frame_count; // = 1
					uint8_t  video_encoding_name_string_length; // = 0
					uint8_t  video_encoding_name[31];
					int16_t  video_pixel_depth; // 24 = RGB888
					int16_t  video_color_table_id; // -1 = no table
					//struct _stsd_avc1_avcC -> variable size
				} box_stsd_avc1;
			} box_stsd = {
					htonl(sizeof(struct _stsd) + stsd_avc1_avcC_size),
					{'s','t','s','d'},
					htonl(0x00000000),
					htonl(1),
					{
							htonl(sizeof(struct _stsd_avc1) + stsd_avc1_avcC_size),
							{'a','v','c','1'},
							{0,},
							htons(1),
							0,
							{0,},
							{0,},
							htons((short)video_w),
							htons((short)video_h),
							htonl(0x00480000),
							htonl(0x00480000),
							{0,},
							htons(1),
							0,
							{0,},
							htons(24),
							htons(-1),
					},
			};

			struct _stts {
				uint32_t size;
				uint8_t  kind[4];
				uint32_t version1_flags3;
				int32_t  entry_count; // 1 (CFR)
				int32_t  frames;
				int32_t  duration; // 1
			} box_stts = {
					htonl(sizeof(struct _stts)),
					{'s','t','t','s'},
					htonl(0x00000000),
					htonl(1),
					htonl(full_frame_cnt),
					htonl(1),
			};

			struct _stss {
				uint32_t size;
				uint8_t  kind[4];
				uint32_t version1_flags3;
				uint32_t sync_frames;
				// uint32_t sync_frame_location[sync_frames];
			} box_stss = {
					htonl(sizeof(struct _stss) + sizeof(uint32_t)*sync_frame_cnt),
					{'s','t','s','s'},
					htonl(0x00000000),
					htonl(sync_frame_cnt),
					// -> frame_infos[...] if(kind=SYNC_PAT)
			};

			struct _stsc {
				uint32_t size;
				uint8_t  kind[4];
				uint32_t version1_flags3;
				int32_t  entry_count; // 1
				uint32_t first_block; // 1
				uint32_t samples_frames; // 1
				uint32_t samples_description_id; // 1
			} box_stsc = {
					htonl(sizeof(struct _stsc)),
					{'s','t','s','c'},
					htonl(0x00000000),
					htonl(1),
					htonl(1),
					htonl(1),
					htonl(1),
			};

			struct _stsz {
				uint32_t size;
				uint8_t  kind[4];
				uint32_t version1_flags3; // = 0
				int32_t  block_byte_size_for_all; // 0 = different sizes
				uint32_t block_byte_sizes_count;
				// uint32_t block_byte_sizes[block_byte_sizes_count];
			} box_stsz = {
					htonl(sizeof(struct _stsz) + sizeof(uint32_t)*full_frame_cnt),
					{'s','t','s','z'},
					htonl(0x00000000),
					htonl(0),
					htonl(full_frame_cnt),
					// -> frame_infos[].size
			};

			struct _stco {
				uint32_t size;
				uint8_t  kind[4];
				uint32_t version1_flags3;
				uint32_t offsets_count;
				// uint32_t offsets[offsets_count];
			} box_stco = {
					htonl(sizeof(struct _stco) + sizeof(uint32_t)*full_frame_cnt),
					{'s','t','c','o'},
					htonl(0x00000000),
					htonl(full_frame_cnt),
					// -> frame_infos[].addr
			};

			uint32_t  var_size =
					sizeof(struct _stsd) + stsd_avc1_avcC_size +
					sizeof(struct _stts) +
					sizeof(struct _stss) + sizeof(uint32_t)*sync_frame_cnt +
					sizeof(struct _stsc) +
					sizeof(struct _stsz) + sizeof(uint32_t)*full_frame_cnt +
					sizeof(struct _stco) + sizeof(uint32_t)*full_frame_cnt;

			struct _udta {
				uint32_t size;
				uint8_t  kind[4];
				struct _meta {
					uint32_t size;
					uint8_t  kind[4];
					uint32_t version1_flags3; // = 0
					struct _meta_hdlr {
						uint32_t size;
						uint8_t  kind[4];
						uint32_t version1_flags3; // = 0
						uint8_t  QUICKTIME_type[4];
						uint8_t  subtype[4];
						uint8_t  QUICKTIME_manufacturer_reserved[4];
						uint32_t QUICKTIME_component_reserved_flags;
						uint32_t QUICKTIME_component_reserved_flags_mask;
						uint8_t  component_type_name[1];
					} box_meta_hdlr;
					struct _ilst {
						uint32_t size;
						uint8_t  kind[4];
						struct _apple_annotation {
							uint32_t size;
							uint8_t  kind[4]; // 0xA9 + 'too'(= encoder)
							struct _data {
								uint32_t size;
								uint8_t  kind[4];
								uint32_t version1_flags3; // 0x00 + 0x000001(= contains text)
								int32_t reserve[1];
								uint8_t annotation_text[10];
							} box_data;
						} box_apple_annotation;
					} box_ilst;
				} box_meta;
			} box_udta = {
					htonl(sizeof(struct _udta)),
					{'u','d','t','a'},
					{
							htonl(sizeof(struct _meta)),
							{'m','e','t','a'},
							htonl(0x00000000),
							{
									htonl(sizeof(struct _meta_hdlr)),
									{'h','d','l','r'},
									htonl(0x00000000),
									{0,},
									{'m','d','i','r'},
									{'a','p','p','l'},
									htonl(0),
									htonl(0),
									{0,},
							},
							{
									htonl(sizeof(struct _ilst)),
									{'i','l','s','t'},
									{
											htonl(sizeof(struct _apple_annotation)),
											{0xA9,'t','o','o'},
											{
													htonl(sizeof(struct _data)),
													{'d','a','t','a'},
													htonl(0x00000001),
													{0,},
													{'L','a','v','f','5','3','.','4','.','0'},
											},
									},
							},
					},
			};

			struct _moov {
				uint32_t size;
				uint8_t  kind[4];
				struct _mvhd {
					uint32_t size;
					uint8_t  kind[4];
					uint32_t version1_flags3;
					uint32_t created_mac_UTC_date;
					uint32_t modified_mac_UTC_date;
					int32_t  timescale;
					int32_t  duration;
					uint32_t user_playback_speed; // (0x00010000 = 1.0)
					uint16_t user_volume; // (0x0100 = 1.0)
					uint8_t  reserve[10];
					uint32_t window_geometry_matrix_value_A; // (0x00010000 = 1.0)
					uint32_t window_geometry_matrix_value_B; // (0x00000000 = 0.0)
					uint32_t window_geometry_matrix_value_U; // (0x00000000 = 0.0)
					uint32_t window_geometry_matrix_value_C; // (0x00000000 = 0.0)
					uint32_t window_geometry_matrix_value_D; // (0x00010000 = 1.0)
					uint32_t window_geometry_matrix_value_V; // (0x00000000 = 0.0)
					uint32_t window_geometry_matrix_value_X; // (0x00000000 = 0.0)
					uint32_t window_geometry_matrix_value_Y; // (0x00000000 = 0.0)
					uint32_t window_geometry_matrix_value_W; // (0x40000000?)
					uint32_t reserve2[6];
					uint32_t next_track_id; // single track = 2
				} box_mvhd;
				struct _trak {
					uint32_t size;
					uint8_t  kind[4];
					struct _tkhd {
						uint32_t size;
						uint8_t  kind[4];
						uint32_t version1_flags3;
						uint32_t created_mac_UTC_date;
						uint32_t modified_mac_UTC_date;
						int32_t  track_id; // first track = 1
						uint32_t reserve[1];
						int32_t  duration;
						uint32_t reserve2[2];
						int16_t  video_layer; // middle = 0
						int16_t  alternate_group;
						uint16_t volume; // (0x0000 = 0.0)
						uint16_t reserve3[1];
						uint32_t video_geometry_matrix_value_A; // (0x00010000 = 1.0)
						uint32_t video_geometry_matrix_value_B; // (0x00000000 = 0.0)
						uint32_t video_geometry_matrix_value_U; // (0x00000000 = 0.0)
						uint32_t video_geometry_matrix_value_C; // (0x00000000 = 0.0)
						uint32_t video_geometry_matrix_value_D; // (0x00010000 = 1.0)
						uint32_t video_geometry_matrix_value_V; // (0x00000000 = 0.0)
						uint32_t video_geometry_matrix_value_X; // (0x00000000 = 0.0)
						uint32_t video_geometry_matrix_value_Y; // (0x00000000 = 0.0)
						uint32_t video_geometry_matrix_value_W; // (0x40000000?)
						uint32_t frame_width;  // Fixed point
						uint32_t frame_height; // Fixed point
					} box_tkhd;
					struct _mdia {
						uint32_t size;
						uint8_t  kind[4];
						struct _mdhd {
							uint32_t size;
							uint8_t  kind[4];
							uint32_t version1_flags3;
							uint32_t created_mac_UTC_date;
							uint32_t modified_mac_UTC_date;
							int32_t  timescale_fps;
							int32_t  duration_frames;
							uint16_t language_code; // ISO-639-2
							uint16_t predefined;
						} box_mdhd;
						struct _hdlr {
							uint32_t size;
							uint8_t  kind[4];
							uint32_t version1_flags3;
							uint32_t component_type;
							uint8_t  subtype[4];
							uint32_t reserve[3];
							uint8_t  component_name[13]; // "VideoHandler"
						} box_hdlr;
						struct _minf {
							uint32_t size;
							uint8_t  kind[4];
							struct _vmhd {
								uint32_t size;
								uint8_t  kind[4];
								uint32_t version1_flags3;
								uint16_t graphics_mode; // 0x0000 = copy
								uint16_t graphics_mode_color[3];
							} box_vmhd;
							struct _dinf {
								uint32_t size;
								uint8_t  kind[4];
								struct _dref {
									uint32_t size;
									uint8_t  kind[4];
									uint32_t version1_flags3;
									int32_t  entry_count; // 1 = minimum
									struct _url {
										uint32_t size;
										uint8_t  kind[4];
										uint32_t version1_flags3; // 1 = internal data
									} box_url;
								} box_dref;
							} box_dinf;
							struct _stbl {
								uint32_t size;
								uint8_t  kind[4];
								// struct _stsd -> variable size
								// struct _stts -> variable size (fix?)
								// struct _stss -> variable size
								// struct _stsc -> variable size (fix?)
								// struct _stsz -> variable size
								// struct _stco -> variable size
							} box_stbl;
						} box_minf;
					} box_mdia;
				} box_trak;
			} box_moov = {
					htonl(sizeof(struct _moov) + var_size + sizeof(struct _udta)),
					{'m','o','o','v'},
					{
							htonl(sizeof(struct _mvhd)),
							{'m','v','h','d'},
							htonl(0x00000000),
							htonl(now),
							htonl(now),
							htonl(1000),
							htonl(duration),
							htonl(0x00010000),
							htons(0x0100),
							{0,},
							htonl(0x00010000),
							htonl(0x00000000),
							htonl(0x00000000),
							htonl(0x00000000),
							htonl(0x00010000),
							htonl(0x00000000),
							htonl(0x00000000),
							htonl(0x00000000),
							htonl(0x40000000),
							{0,},
							htonl(2),
					},
					{
							htonl(sizeof(struct _trak) + var_size),
							{'t','r','a','k'},
							{
									htonl(sizeof(struct _tkhd)),
									{'t','k','h','d'},
									htonl(0x0000000F),
									htonl(now),
									htonl(now),
									htonl(1),
									{0,},
									htonl(duration),
									{0,},
									htons(0),
									htons(0),
									htons(0x0000),
									{0,},
									htonl(0x00010000),
									htonl(0x00000000),
									htonl(0x00000000),
									htonl(0x00000000),
									htonl(0x00010000),
									htonl(0x00000000),
									htonl(0x00000000),
									htonl(0x00000000),
									htonl(0x40000000),
									htonl(video_w<<16),
									htonl(video_h<<16),
							},
							{
									htonl(sizeof(struct _mdia) + var_size),
									{'m','d','i','a'},
									{
											htonl(sizeof(struct _mdhd)),
											{'m','d','h','d'},
											htonl(0x00000000),
											htonl(now),
											htonl(now),
											htonl(fps),
											htonl(full_frame_cnt),
											// 0x55C4 = "und(Undetermined)"
											// -> ISO-639-2
											//   u = 75h -(-60h)-> 15h -> 10101b
											//   n = 6Eh -(-60h)-> 0Eh -> 01110b
											//   d = 64h -(-60h)-> 04h -> 00100b
											//   10101:01110:00100b = 101:0101:1100:0100b = 55C4h
											htons(0x55C4),
											htons(0),
									},
									{
											htonl(sizeof(struct _hdlr)),
											{'h','d','l','r'},
											htonl(0x00000000),
											htonl(0),
											{'v','i','d','e'},
											{0,},
											{'V','i','d','e','o','H','a','n','d','l','e','r',0x0},
									},
									{
											htonl(sizeof(struct _minf) + var_size),
											{'m','i','n','f'},
											{
													htonl(sizeof(struct _vmhd)),
													{'v','m','h','d'},
													htonl(0x00000001),
													htons(0x0000),
													{htons(0x0000),htons(0x0000),htons(0x0000)},
											},
											{
													htonl(sizeof(struct _dinf)),
													{'d','i','n','f'},
													{
															htonl(sizeof(struct _dref)),
															{'d','r','e','f'},
															htonl(0x00000000),
															htonl(1),
															{
																	htonl(sizeof(struct _url)),
																	{'u','r','l',' '},
																	htonl(0x00000001),
															},
													},
											},
											{
													htonl(sizeof(struct _stbl) + var_size),
													{'s','t','b','l'},
													// struct _stsd -> variable size
													// struct _stts -> variable size (fix?)
													// struct _stss -> variable size
													// struct _stsc -> variable size (fix?)
													// struct _stsz -> variable size
													// struct _stco -> variable size
											},
									},
							},
					},
			};

			//
			dst_addr += fwrite(&box_moov, sizeof(box_moov), 1, dst_fp) * sizeof(box_moov);
			dst_addr += fwrite(&box_stsd, sizeof(box_stsd), 1, dst_fp) * sizeof(box_stsd);
			dst_addr += fwrite(&box_stsd_avc1_avcC_part1, sizeof(box_stsd_avc1_avcC_part1), 1, dst_fp) * sizeof(box_stsd_avc1_avcC_part1);
			dst_addr += fwrite(sps_dat, 1, sps_len, dst_fp);
			dst_addr += fwrite(&box_stsd_avc1_avcC_part2, sizeof(box_stsd_avc1_avcC_part2), 1, dst_fp) * sizeof(box_stsd_avc1_avcC_part2);
			dst_addr += fwrite(pps_dat, 1, pps_len, dst_fp);
			dst_addr += fwrite(&box_stts, sizeof(box_stts), 1, dst_fp) * sizeof(box_stts);
			{
				dst_addr += fwrite(&box_stss, sizeof(box_stss), 1, dst_fp) * sizeof(box_stss);
				uint32_t tmp;
				for (i=0; i<frame_info_cnt; i++) {
					if (frame_infos[i].nal_type == NAL_TYPE_SYNC) {
						tmp = htonl(frame_infos[i].frame_idx + 1);
						dst_addr += fwrite(&tmp, sizeof(tmp), 1, dst_fp) * sizeof(tmp);
					}
				}
			}
			dst_addr += fwrite(&box_stsc, sizeof(box_stsc), 1, dst_fp) * sizeof(box_stsc);
			{
				dst_addr += fwrite(&box_stsz, sizeof(box_stsz), 1, dst_fp) * sizeof(box_stsz);
				uint32_t tmp = 0;
				for (i=0; i<frame_info_cnt; i++) {
					tmp += frame_infos[i].size + sizeof(uint32_t);
					if (frame_infos[i].frame_idx >= 0) {
						tmp = htonl(tmp);
						dst_addr += fwrite(&tmp, sizeof(tmp), 1, dst_fp) * sizeof(tmp);
						tmp = 0;
					}
				}
			}
			{
				dst_addr += fwrite(&box_stco, sizeof(box_stco), 1, dst_fp) * sizeof(box_stco);
				uint32_t org = dst_addr + sizeof(uint32_t)*full_frame_cnt + sizeof(box_udta) + 8/*sizeof(box_mdat)*/;
				uint32_t tmp = 0;
				for (i=0; i<frame_info_cnt; i++) {
					if (tmp == 0) {
						tmp = htonl(org + frame_infos[i].addr);
					}
					if (frame_infos[i].frame_idx >= 0) {
						dst_addr += fwrite(&tmp, sizeof(tmp), 1, dst_fp) * sizeof(tmp);
						tmp = 0;
					}
				}
			}
			dst_addr += fwrite(&box_udta, sizeof(box_udta), 1, dst_fp) * sizeof(box_udta);
		}

		// Dst: MDAT
		{
			struct _mdat {
				uint32_t size;
				uint8_t  kind[4];
				//uint8_t  sample_data[full_frame_cnt];
			} box_mdat = {
					htonl(sizeof(struct _mdat) + src_addr),
					{'m','d','a','t'},
			};
			dst_addr += fwrite(&box_mdat, sizeof(box_mdat), 1, dst_fp) * sizeof(box_mdat);

			// Src -> Dst
			uint32_t tmp = 0;
			uint32_t cnt = 0;
			for (i=0,j=0; i<frame_info_cnt && !feof(src_fp); ) {
				num = fread(buf, 1, BUF_SIZE, src_fp);
				bufP = buf;
				while(num > 0) {
					if (j == 0) {
						j = frame_infos[i].size;
						tmp = htonl(j);
						dst_addr += fwrite(&tmp, sizeof(tmp), 1, dst_fp) * sizeof(tmp);
						cnt = sizeof(tmp);
					}
					if (cnt > 0) {
						if (num >= cnt) {
							bufP += cnt;
							num  -= cnt;
							cnt   = 0;
						} else {
							bufP += num;
							cnt  -= num;
							num   = 0;
						}
					} else if (num >= j) {
						dst_addr += fwrite(bufP, 1, j, dst_fp);
						bufP += j;
						num  -= j;
						j     = 0;
						i++;
					} else {
						dst_addr += fwrite(bufP, 1, num, dst_fp);
						bufP += num;
						j    -= num;
						num   = 0;
					}
				}
			}
		}
	} while(0);

	//
	if (src_fp != NULL) {
		fclose(src_fp);
	}
	if (dst_fp != NULL) {
		fclose(dst_fp);
	}
	if (buf != NULL) {
		free(buf);
	}
	if (frame_infos != NULL) {
		free(frame_infos);
	}
	if (sps_dat != NULL) {
		free(sps_dat);
	}
	if (pps_dat != NULL) {
		free(pps_dat);
	}
	return rcode;
}
