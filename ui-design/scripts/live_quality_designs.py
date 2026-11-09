"""Live quality UI uses the observed CCTV-1 and CCTV-1高清 variants; no fake SD label."""
def install(g):
    def menu(large=False):
        s=g['live_bar'](active='画质',channel='01  CCTV-1高清')
        return s+g['track_popover']('画质',[('高清','CCTV-1高清'),('CCTV-1','画质未标注')],0,1224,s,large=large)
    def additional():
        cases=[('05-live-quality','直播：当前频道画质菜单',menu()),
               ('05-live-quality-single','直播：仅一个未标注画质版本',g['live_bar'](quality='画质',quality_available=False,channel='01  纪实频道')),
               ('05-live-quality-failed','直播：画质切换失败保留原播放',g['live_bar'](active='画质',channel='01  CCTV-1高清')+g['rect'](600,786,720,64,'#101629',14)+g['centered_text'](960,828,'画质切换失败，继续播放原画质',28)),
               ('05-live-quality-large','直播：画质菜单150%字号',menu(True))]
        for slug,title,body in cases:g['emit'](slug,title,body);g['SCREENS'][-1]['kind']='state'
    return additional
