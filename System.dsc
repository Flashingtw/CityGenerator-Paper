city_create:
    type: command
    name: citycreate
    description: 生成城市
    usage: /citycreate <&lt>arg<&gt>
    permission: dgspawn.use
    script:
    # 模板由 WorldEdit 在貼上時直接讀取，避免 Denizen 無法解析新版 Sponge 格式 .schem
    - define args <context.args>
    - define map_size <[args].get[1]>
    - define vis <map[]>

    # 取得玩家腳下整數座標
    - define p_loc <player.location.block>
    - define start <location[<[map_size].add[1]>,0,<[map_size].add[1]>]>

    - define q <list[<map[loc=<[start]>;dir=0]>]>

    - define lmt <[map_size].mul[2].add[1]>

    - repeat <[lmt]> as:x:
        - repeat <[lmt]> as:z:
            - define vis <[vis].with[<location[<[x]>,0,<[z]>]>].as[false]>

    - define vis <[vis].with[<[start]>].as[true]>

# =============================
    # 1=X+, 2=Z+, 3=X-, 4=Z-
    - define dx <list[1|0|-1|0]>
    - define dz <list[0|1|0|-1]>
    - define adj <map[]>
    - define tick 0

    - while !<[q].is_empty>:
        - define tick:++
        - if <[tick].mod[2]> == 0:
            - wait 1t
        - actionbar "<&e>城市路網生成中... " targets:<server.online_players>
        - if <player.has_flag[stopdungeon]>:
            - while stop

# =============================
        - define idx <util.random.int[1].to[<[q].size>]>
        - define cur_node <[q].get[<[idx]>]>
        - define q <[q].remove[<[idx]>]>
        - define cur <[cur_node].get[loc]>
        - define prev_dir <[cur_node].get[dir]>

        - define dist_x <[cur].x.sub[<[start].x>].abs>
        - define dist_z <[cur].z.sub[<[start].z>].abs>
        - define dist <[dist_x].add[<[dist_z]>]>

        - define rnd <util.random.int[0].to[100]>

        - if <[dist]> == 0:
            - define deglmt 4
        - else:
            - if <[dist]> <= 2:
                - define t3 40
                - define t2 20
                - define t1 0
            - else if <[dist]> <= 4:
                - define t3 80
                - define t2 60
                - define t1 30
            - else:
                - define t3 95
                - define t2 85
                - define t1 50

            - if <[rnd]> > <[t3]>:
                - define deglmt 3
            - else if <[rnd]> > <[t2]>:
                - define deglmt 2
            - else if <[rnd]> > <[t1]>:
                - define deglmt 1
            - else:
                - define deglmt 0
        - define numlst <list[1|2|3|4].random[4]>
        - if <[prev_dir]> != 0:
            - define numlst <[numlst].exclude[<[prev_dir]>]>
            - define numlst <[numlst].insert[<[prev_dir]>].at[1]>

        - define cnt 0
        - foreach <[numlst]> as:num:
            - if <[cnt]> >= <[deglmt]>:
                - foreach stop
            - define dloc <location[<[dx].get[<[num]>]>,0,<[dz].get[<[num]>]>]>
            - define next_loc <[cur].add[<[dloc]>]>

            - if <[next_loc].x> < 1 or <[next_loc].z> < 1 or <[next_loc].x> > <[lmt]> or <[next_loc].z> > <[lmt]>:
                - foreach next
            - if <[vis].get[<[next_loc]>]>:
                - foreach next

            - define vis <[vis].with[<[next_loc]>].as[true]>

            - define q:->:<map[loc=<[next_loc]>;dir=<[num]>]>

            - define curadj <[adj].get[<[cur]>]||<list[]>>
            - define curadj:->:<[next_loc]>

            - define nxtadj <[adj].get[<[next_loc]>]||<list[]>>
            - define nxtadj:->:<[cur]>

            - define adj <[adj].with[<[cur]>].as[<[curadj]>]>
            - define adj <[adj].with[<[next_loc]>].as[<[nxtadj]>]>
            - define cnt <[cnt].add[1]>

    # 傳入 start 座標作對齊
    - run city_build def:<[p_loc]>|<[adj]>|<[start]>
    - announce "<&a>城市生成完畢！"

city_build:
    type: task
    definitions: p_loc|adj|start
    script:
    # 7x7 的模板間距設為 7
    - define spacing 7

    - foreach <[adj]> key:key_raw as:val:
        # 將 Key 強制轉為座標物件
        - define key <location[<[key_raw]>]>

        # 以 start 點為原點，計算相對偏移，讓城市中心剛好落於玩家腳下
        - define offset <[key].sub[<[start]>]>
        - define scaled_offset <[offset].mul[<[spacing]>]>
        - define node_loc <[p_loc].add[<[scaled_offset]>]>

        # 1. 偵測 4 個方向連接
        - define mask 0
        - foreach <[val]> as:f_val_raw:
            - wait 1t
            - define f_val <location[<[f_val_raw]>]>
            - if <[f_val].z> < <[key].z>:
                - define mask <[mask].add[1]>
                # 北 (Z-)
            - else if <[f_val].x> > <[key].x>:
                - define mask <[mask].add[2]>
                # 東 (X+)
            - else if <[f_val].z> > <[key].z>:
                - define mask <[mask].add[4]>
                # 南 (Z+)
            - else if <[f_val].x> < <[key].x>:
                - define mask <[mask].add[8]>
                # 西 (X-)

        # 2. 透過遮罩分數決定模型與角度
        - define angle 0
        - define schem_name "cross"

        - choose <[mask]>:
            # ==== 死胡同 (Deadend) ====
            - case 1:
                - define schem_name "deadend"
                - define angle 180
            - case 2:
                - define schem_name "deadend"
                - define angle 270
            - case 4:
                - define schem_name "deadend"
                - define angle 0
            - case 8:
                - define schem_name "deadend"
                - define angle 90

            # ==== 直線 (Straight) ====
            - case 5:
                - define schem_name "straight"
                - define angle 0
            - case 10:
                - define schem_name "straight"
                - define angle 90

            # ==== 轉角 (Corner) ====
            - case 3:
                - define schem_name "corner"
                - define angle 270
            - case 6:
                - define schem_name "corner"
                - define angle 0
            - case 12:
                - define schem_name "corner"
                - define angle 90
            - case 9:
                - define schem_name "corner"
                - define angle 180

            # ==== T字路口 (T-Junction) ====
            - case 11:
                - define schem_name "t_junction"
                - define angle 180
            - case 7:
                - define schem_name "t_junction"
                - define angle 270
            - case 14:
                - define schem_name "t_junction"
                - define angle 0
            - case 13:
                - define schem_name "t_junction"
                - define angle 90

            # ==== 十字路口 (Crossroad) ====
            - case 15:
                - define schem_name "cross"
                - define angle 0

            - default:
                - define schem_name "cross"
                - define angle 0

        # 3. 由 WorldEdit / FAWE 直接讀取並貼上新版 Sponge .schem
        - if !<schematic[<[schem_name]>].exists>:
            - ~schematic load name:<[schem_name]> filename:<[schem_name]>.schem

        # 在指定座標貼上，並套用旋轉角度 (0, 90, 180, 270)
        - schematic paste name:<[schem_name]> <[node_loc]> angle:<[angle]>

stop_dungeon:
    type: command
    name: stopdungeon
    description: 停止地牢生成
    usage: /stopdungeon
    permission: stopdungeon.use
    script:
    - flag <player> stopdungeon:true expire:5s