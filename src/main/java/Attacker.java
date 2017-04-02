/**
 * Created by lydakis-local on 4/2/17.
 */
public class Attacker extends Player {

    private int bombs;
    private double speed;
    private double attackRating;
    private long lastAttack;
    private long lastBomb;
    private long lastBoost;
    private int toLevelUpAr = 10;
    private int toLevelUpSpeed = 10;

    public Attacker(String un, int s, int cr){
        super(un, s, cr);
        this.speed = 1.0;
        this.attackRating = 1.0;
        this.lastAttack = -1l;
        this.lastBomb = -1l;
        this.bombs = 0;
    }

    public double getSpeed(){
        return this.speed;
    }

    public double getAttackRating(){
        return this.attackRating;
    }

    public int getBombs(){
        return this.bombs;
    }

    public boolean canAttack(){
        return ((System.nanoTime() - this.lastAttack)>this.speed);
    }

    public boolean canBoost(){
        return ((System.nanoTime() - this.lastBoost)>getBoostCooldown());
    }

    public void levelUpAr(){
        int cr = getCredits();
        if(getCredits()<=toLevelUpAr){
            attackRating+=1;
            toLevelUpAr *=10;
            return;
        }
        System.err.println("Need "+toLevelUpAr+" credits to level up attack rating, current credits: "+cr);
    }

    public void levelUpSpeed(){
        int cr = getCredits();
        if(getCredits()<=toLevelUpSpeed){
            speed+=1;
            toLevelUpSpeed *=10;
            return;
        }
        System.err.println("Need "+toLevelUpSpeed+" credits to level up speed, current credits: "+cr);
    }
}