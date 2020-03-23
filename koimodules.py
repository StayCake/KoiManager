import os.path
import datetime
import aiofiles

from os import path

class date:
    def datefm():
        dt = datetime.datetime.now()
        return dt.strftime("%Y-%m-%d")
    def load():
        dt = datetime.datetime.now()
        return dt.strftime("%Y년 %m월 %d일 | %H시 %M분 %S초 ) ")

class daily:
    async def check(message,self):
        if not path.exists("log.kbd"):
        else:
            checkdata = async File.get('UserDB/daily/' + message.author.id)

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
        await log.send(logd + "\n")
