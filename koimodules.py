import os.path
import datetime
import aiofiles
import discord

from os import path

# 각종 클래스 모듈 정의 파일

class date:
    def datefm():
        dt = datetime.datetime.now()
        return dt.strftime("%Y-%m-%d")
    def load():
        dt = datetime.datetime.now()
        return dt.strftime("%Y년 %m월 %d일 | %H시 %M분 %S초 ) ")

class daily:
    async def check(message,self):
        
        if not os.path.isdir(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily_'):
            os.makedirs(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily_')
            
        if path.exists(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily_' + str(message.author.id) + '.kbd') and path.exists(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily___guilddata__.kbd'):
            checkdata = await File.get(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily_' + str(message.author.id))
            ckdate, rankstr = checkdata.split(',')
            # 체크용 구문 : tod
            # rankint = int(rankstr)
            if str(ckdate) == date.datefm():
                check = 'did'
            else:
                check = 'tmr'
        elif not path.exists(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily_' + str(message.author.id) + '.kbd') and path.exists(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily___guilddata__.kbd'):
            check = 'nud'
        else:
            check = 'ngd'
            
        if check == 'ngd':
            guilddata = 'oneday,0'
        else:
            guilddata = await File.get(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily___guilddata__')
            
        guilddate, guildrkstr = guilddata.split(',')
        
        if guilddate != date.datefm():
            guilddate = 'oneday'
            guildrkstr = '0'
            
        guildrkint = int(guildrkstr)
        
        if check != 'did':
            await File.write(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily_' + str(message.author.id),date.datefm() + ',' + str(guildrkint + 1))
            await File.write(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '\\daily___guilddata__',date.datefm() + ',' + str(guildrkint + 1))
            embed = discord.Embed(title="출첵!", description="출석이 처리 되었습니다.", color=0x62c1cc)
            embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
            embed.add_field(name="출석 일자", value=str(date.datefm()), inline=True)
            embed.add_field(name="서버 순위", value=str(guildrkint + 1) + '위', inline=True)
            await message.channel.send(embed=embed)
        else:
            embed = discord.Embed(title="오늘은 여기까지!", description="이미 출석하셨습니다.", color=0x62c1cc)
            embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
            embed.add_field(name="출석 일자", value=str(date.datefm()), inline=True)
            await message.channel.send(embed=embed)
            
        return

class File:
    async def write(file, data):
        async with aiofiles.open(file + ".kbd", mode='w', encoding='UTF8') as f:
            await f.write(data)
        return
    async def append(file, data):
        async with aiofiles.open(file + ".kbd", mode='a', encoding='UTF8') as f:
            await f.write(data)
        return
    async def get(file):
        async with aiofiles.open(file + ".kbd", mode='r', encoding='UTF8') as f:
            rtd = await f.read()
        return rtd

class log:
    async def send(data):
        if not path.exists("log.kbd"):
            await File.write("log",data)
            return
        else:
            await File.append("log",data)
            return
    async def write(msg,md):
        if md == 'cmdget':
            logd = date.load() + '명령어 수신 [' + msg.author.name + '] : ' + msg.content
        elif md == 'slogin':
            logd = date.load() + '로그인 : {0}'.format(msg.user)
        elif md == 'sndmsg':
            logd = date.load() + '메시지 전송 : ' + msg.content
        elif md == 'sutdwn':
            logd = date.load() + '시스템 종료.'
        elif md == 'cmddnd':
            logd = date.load() + '권한 부족 [' + msg.author.name + '] : ' + msg.content
        print(logd)
        await log.send(logd + "\\n")
