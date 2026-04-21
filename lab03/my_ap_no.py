from ursina import *

app = Ursina()
window.title = 'Battle Arena'

bg_music = Audio('skrillex.mp3', loop=True, autoplay=False, volume=0.5)
FIELD_BOUNDARY = 24


class Unit(Entity):
    UNIT_STATS = {
        'melee': {'hp': 100, 'cost': 10, 'model': 'cube', 'scale': (1.5, 2.5, 1.5)},
        'ranged': {'hp': 80, 'cost': 15, 'model': 'sphere', 'scale': (1.3, 2.0, 1.3)},
        'tank': {'hp': 200, 'cost': 25, 'model': 'cube', 'scale': (2.0, 3.0, 2.0)},
        'assassin': {'hp': 60, 'cost': 20, 'model': 'sphere', 'scale': (1.0, 2.0, 1.0)},
        'healer': {'hp': 90, 'cost': 20, 'model': 'sphere', 'scale': (1.4, 2.3, 1.4)},
    }

    def __init__(self, pos, team, unit_type):
        stats = self.UNIT_STATS.get(unit_type, self.UNIT_STATS['melee'])

        type_colors = {
            'melee': color.blue if team == 1 else color.red,
            'ranged': color.cyan if team == 1 else color.orange,
            'tank': color.rgb(0, 0, 150) if team == 1 else color.rgb(150, 0, 0),
            'assassin': color.rgb(150, 0, 150) if team == 1 else color.yellow,
            'healer': color.lime if team == 1 else color.green,
        }

        super().__init__(
            model=stats['model'],
            color=type_colors.get(unit_type, color.white),
            position=pos,
            scale=stats['scale'],
            collider='box',
            texture='white_cube'
        )

        self.team = team
        self.unit_type = unit_type
        self.health = stats['hp']
        self.max_health = stats['hp']

    def update(self):
        if not bss.battle_started: return
        if self.health <= 0:
            self.color = color.gray
            invoke(destroy, self, delay=2)
            return

        enemies = bss.team2 if self.team == 1 else bss.team1
        target = None
        min_dist = 999
        for e in enemies:
            if e and e.health > 0:
                d = distance(self.position, e.position)
                if d < min_dist:
                    min_dist = d
                    target = e

        if self.unit_type == 'melee':
            if target:
                if distance(self.position, target.position) > 2.0:
                    self.look_at(target);
                    self.position += self.forward * time.dt * 5
                else:
                    target.health -= time.dt * 40

        elif self.unit_type == 'ranged':
            if target:
                dist = distance(self.position, target.position)
                if dist > 10:
                    self.look_at(target); self.position += self.forward * time.dt * 4
                elif dist < 6:
                    self.look_at(target); self.position -= self.forward * time.dt * 3
                if dist < 15:
                    dmg = time.dt * 20
                    if target.unit_type == 'assassin': dmg *= 0.3
                    target.health -= dmg

        elif self.unit_type == 'tank':
            if target:
                if distance(self.position, target.position) > 3.0:
                    self.look_at(target);
                    self.position += self.forward * time.dt * 2
                else:
                    target.health -= time.dt * 25

        elif self.unit_type == 'assassin':
            if target:
                dist = distance(self.position, target.position)
                if 7.0 < dist < 15.0:
                    self.look_at(target); self.position += self.forward * time.dt * 20
                elif dist > 2.0:
                    self.look_at(target); self.position += self.forward * time.dt * 8
                else:
                    target.health -= time.dt * 60

        elif self.unit_type == 'healer':
            allies = bss.team1 if self.team == 1 else bss.team2
            heal_target = None
            min_hp = 100
            for ally in allies:
                if ally and 0 < ally.health < 100:
                    d = distance(self.position, ally.position)
                    if d < 10 and ally.health < min_hp:
                        min_hp = ally.health;
                        heal_target = ally
            if heal_target:
                if distance(self.position, heal_target.position) > 5:
                    self.look_at(heal_target);
                    self.position += self.forward * time.dt * 4
                else:
                    heal_target.health = min(100, heal_target.health + time.dt * 30)
            elif target:
                dist = distance(self.position, target.position)
                if dist > 10: self.look_at(target); self.position += self.forward * time.dt * 4
                if dist < 15: target.health -= time.dt * 15

        self.position.x = clamp(self.position.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
        self.position.z = clamp(self.position.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)


class BattleSystem:
    def __init__(self):
        self.team1, self.team2 = [], []
        self.p1_money, self.p2_money = 100, 100
        self.current_player, self.selected_type = 1, 'melee'
        self.battle_started = False
        self.ui = Text(text='', position=(-0.85, 0.45), scale=1.5, color=color.blue)
        self.money_text = Text(text='', position=(-0.85, 0.40), scale=1.2, color=color.gold)
        self.desc_text = Text(text='1:Воин  2:Лучник  3:Танк  4:Убийца  5:Целитель', position=(-0.85, 0.35), scale=1.1,
                              color=color.gray)
        self.ghost = Entity(model='cube', color=color.rgba(0, 255, 0, 150), scale=(1.5, 2.5, 1.5), alpha=0.5)

    DESCRIPTIONS = {
        'melee': 'Воин - сбалансированный боец',
        'ranged': 'Лучник - атакует с дистанции',
        'tank': 'Танк - много здоровья',
        'assassin': 'Убийца - быстрый, прыгает к цели',
        'healer': 'Целитель - лечит союзников',
    }


bss = BattleSystem()


def update():
    if bss.battle_started: return

    money = bss.p1_money if bss.current_player == 1 else bss.p2_money
    bss.money_text.text = f'Золото: {money}'
    desc = bss.DESCRIPTIONS.get(bss.selected_type, '')
    cost = Unit.UNIT_STATS[bss.selected_type]['cost']
    bss.ui.text = f'P{bss.current_player}: {desc} ({cost} золота)'

    if mouse.hovered_entity == ground:
        gp = mouse.world_point
        bss.ghost.position = Vec3(round(gp.x), 1.25, round(gp.z))
        bss.ghost.enabled = True
        stats = Unit.UNIT_STATS[bss.selected_type]
        bss.ghost.scale = stats['scale']
        zone_ok = (bss.current_player == 1 and bss.ghost.x < -1) or (bss.current_player == 2 and bss.ghost.x > 1)
        bss.ghost.color = color.rgba(0, 255, 0, 150) if (zone_ok and money >= stats['cost']) else color.rgba(255, 0, 0,
                                                                                                             150)
    else:
        bss.ghost.enabled = False


def input(key):
    if bss.battle_started: return
    m = {'1': 'melee', '2': 'ranged', '3': 'tank', '4': 'assassin', '5': 'healer'}
    if key in m: bss.selected_type = m[key]
    if key == 'left mouse down' and bss.ghost.enabled and bss.ghost.color == color.rgba(0, 255, 0, 150):
        u = Unit(bss.ghost.position, bss.current_player, bss.selected_type)
        if bss.current_player == 1:
            bss.team1.append(u); bss.p1_money -= Unit.UNIT_STATS[bss.selected_type]['cost']
        else:
            bss.team2.append(u); bss.p2_money -= Unit.UNIT_STATS[bss.selected_type]['cost']
    if key == 'enter':
        if bss.current_player == 1:
            bss.current_player = 2; bss.ui.color = color.red
        else:
            bss.battle_started = True;
            bg_music.play();
            bss.ghost.enabled = False
            bss.ui.text = 'БОЙ НАЧАЛСЯ!';
            bss.ui.color = color.yellow
            bss.desc_text.enabled = bss.money_text.enabled = False


ground = Entity(model='cube', scale=(50, 0.5, 50), color=color.white, collider='box', y=-0.25, texture='white_cube')
line = Entity(model='cube', scale=(0.5, 0.1, 50), color=color.black, z=0, y=0.1)
Sky(color=color.cyan)
AmbientLight(color=color.white)
DirectionalLight(y=20, rotation=(45, 45, 45), shadows=True)

camera.position = (0, 25, -200);
camera.look_at(ground);
EditorCamera()

app.run()
